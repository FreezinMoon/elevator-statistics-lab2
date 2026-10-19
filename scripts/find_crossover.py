"""Read two JMH JSON files and locate sequential/parallel time crossings."""

import json
import math
import sys
from pathlib import Path


def load_results(path):
    results = {}
    for row in json.loads(Path(path).read_text(encoding="utf-8")):
        name = row["benchmark"]
        method = name.rsplit(".", 1)[-1]
        size = int(row["params"]["size"])
        metric = row["primaryMetric"]
        results.setdefault(size, {})[method] = (
            float(metric["score"]),
            float(metric.get("scoreError") or 0.0),
            metric["scoreUnit"],
        )
    return results


def describe(path):
    results = load_results(path)
    print(f"\n{path}")
    print("N        sequential       parallel        forkJoin       comparison")
    pairs = []
    for size, methods in sorted(results.items()):
        if "sequential" not in methods or "parallel" not in methods:
            continue
        seq, seq_error, unit = methods["sequential"]
        par, par_error, par_unit = methods["parallel"]
        if unit != par_unit:
            raise ValueError(f"Different units for size {size}: {unit}, {par_unit}")
        custom = methods.get("forkJoin", (math.nan, 0, unit))[0]
        near = abs(seq - par) <= seq_error + par_error
        winner = "parallel faster" if par < seq else "sequential faster"
        label = winner + (" (intervals overlap)" if near else "")
        print(f"{size:<8} {seq:12.3f} {par:14.3f} {custom:15.3f}   {label} ({unit})")
        pairs.append((size, seq, par))

    crossings = []
    for (n1, s1, p1), (n2, s2, p2) in zip(pairs, pairs[1:]):
        d1 = p1 - s1
        d2 = p2 - s2
        if d1 == 0:
            crossings.append((n1, n1, float(n1)))
        elif d1 * d2 < 0:
            fraction = -d1 / (d2 - d1)
            estimate = math.exp(math.log(n1) + fraction * (math.log(n2) - math.log(n1)))
            crossings.append((n1, n2, estimate))
    if pairs and pairs[-1][2] == pairs[-1][1]:
        n = pairs[-1][0]
        crossings.append((n, n, float(n)))
    if crossings:
        for lower, upper, estimate in crossings:
            precision = ".1f" if estimate < 10 else ".0f"
            print(f"Sign change between {lower} and {upper} events; "
                  f"interpolated estimate {format(estimate, precision)}")
        if len(crossings) > 1:
            print("Several sign changes: there is no unique stable crossover in this run.")
        print("Overlapping JMH error intervals do not prove equal speed.")
    else:
        print("No crossover in the measured sizes; expand the range or repeat the run.")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit("Usage: python find_crossover.py target/no-delay.json target/with-delay.json")
    for filename in sys.argv[1:]:
        describe(filename)
