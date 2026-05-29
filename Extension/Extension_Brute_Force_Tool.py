"""
Extension_Brute_Force_Tool.py
====================
Proves ground truth optimality for all 8 Extension preset maps.

Method:
  - Single-warehouse maps (01-04, 07): enumerate ALL capacity-feasible
    customer partitions across DAs, then find optimal route order per group
    using itertools.permutations. The minimum found is the proven global optimum.
  - Multi-warehouse maps (05, 06, 08): each warehouse has exactly one DA.
    Enumerate all orderings of that DA's customers (single-group permutation).

No external libraries required — uses only Python built-ins: math, itertools, time.

Run:
    python Extension_Brute_Force_Tool.py
"""

import math
import itertools
import time

# ── Depot positions ───────────────────────────────────────────────────────────
DEPOT = (50, 50)
WH0   = (20, 20)
WH1   = (80, 20)
WH2   = (50, 80)


def euclid(a, b):
    return math.sqrt((a[0]-b[0])**2 + (a[1]-b[1])**2)


def route_dist(route, depot):
    """Total distance: depot -> customers in order -> depot."""
    if not route:
        return 0.0
    total = euclid(depot, route[0])
    for i in range(len(route) - 1):
        total += euclid(route[i], route[i+1])
    total += euclid(route[-1], depot)
    return total


def best_perm(group_indices, customers, depot):
    """Find optimal visiting order for a fixed group. Returns (route, cost)."""
    if not group_indices:
        return [], 0.0
    best_cost  = float('inf')
    best_route = []
    for perm in itertools.permutations(group_indices):
        route = [customers[i] for i in perm]
        cost  = route_dist(route, depot)
        if cost < best_cost:
            best_cost  = cost
            best_route = route[:]
    return best_route, best_cost


def brute_force_multi_da(customers, caps, depot):
    """
    Full brute force for multiple DAs sharing one depot.
    Enumerates every capacity-feasible partition, finds optimal order per group.
    Returns (assignments, total_cost, partitions_checked).
    """
    n      = len(customers)
    num_da = len(caps)
    best_cost  = float('inf')
    best_asgn  = None
    n_parts    = 0

    def gen_partitions(remaining, da_left, caps_left):
        if da_left == 1:
            if len(remaining) <= caps_left[0]:
                yield [list(remaining)]
            return
        for size in range(0, len(remaining) + 1):
            for group in itertools.combinations(remaining, size):
                if len(group) > caps_left[0]:
                    continue
                rest = [x for x in remaining if x not in group]
                for rp in gen_partitions(rest, da_left - 1, caps_left[1:]):
                    yield [list(group)] + rp

    for partition in gen_partitions(list(range(n)), num_da, caps):
        n_parts += 1
        total  = 0.0
        routes = []
        for grp in partition:
            route, cost = best_perm(grp, customers, depot)
            total  += cost
            routes.append(route)
        if total < best_cost:
            best_cost = total
            best_asgn = [r[:] for r in routes]

    return best_asgn, best_cost, n_parts


def brute_force_single_da(customers, depot):
    """
    Single DA: find optimal ordering of all customers from this depot.
    Returns (route, cost, orderings_checked).
    """
    best_cost  = float('inf')
    best_route = customers[:]
    n_orders   = 0
    for perm in itertools.permutations(customers):
        n_orders += 1
        cost = route_dist(list(perm), depot)
        if cost < best_cost:
            best_cost  = cost
            best_route = list(perm)
    return best_route, best_cost, n_orders


# ── Map definitions ───────────────────────────────────────────────────────────

MAPS = [
    {
        "id": "01", "name": "ext_map01_compass",
        "desc": "Single WH | 2 DAs cap=3 | 4 customers at compass points",
        "mode": "single_wh", "depot": DEPOT,
        "customers": [(50,85),(85,50),(50,15),(15,50)],
        "caps": [3, 3], "gt": 238.9949,
        "n_partitions": 14, "n_orderings": 72,
    },
    {
        "id": "02", "name": "ext_map02_two_clusters",
        "desc": "Single WH | 2 DAs cap=5 | 8 customers in 2 clusters",
        "mode": "single_wh", "depot": DEPOT,
        "customers": [(10,80),(15,85),(5,75),(20,80),(85,15),(90,20),(80,10),(90,10)],
        "caps": [5, 5], "gt": 239.2599,
        "n_partitions": 182, "n_orderings": "per-group",
    },
    {
        "id": "03", "name": "ext_map03_linear",
        "desc": "Single WH | 2 DAs cap=3 | 6 customers on y=50 line",
        "mode": "single_wh", "depot": DEPOT,
        "customers": [(5,50),(25,50),(40,50),(60,50),(75,50),(95,50)],
        "caps": [3, 3], "gt": 180.0000,
        "n_partitions": 20, "n_orderings": "per-group",
    },
    {
        "id": "04", "name": "ext_map04_medium",
        "desc": "Single WH | 3 DAs cap=4 | 10 customers in 2 zones",
        "mode": "single_wh", "depot": DEPOT,
        "customers": [(15,20),(30,10),(10,35),(35,30),(20,50),(70,70),(85,80),(80,55),(65,85),(90,65)],
        "caps": [4, 4, 4], "gt": 352.0850,
        "n_partitions": 22050, "n_orderings": "per-group",
    },
    {
        "id": "05", "name": "ext_map05_3wh_local",
        "desc": "3 WH | 1 DA per WH cap=3 | 3 customers per warehouse",
        "mode": "multi_wh",
        "warehouses": [
            {"depot": WH0, "customers": [(15,15),(25,25),(10,30)],  "cap": 3},
            {"depot": WH1, "customers": [(75,15),(85,25),(90,10)],  "cap": 3},
            {"depot": WH2, "customers": [(45,75),(55,85),(50,92)],  "cap": 3},
        ],
        "gt": 131.9943,
    },
    {
        "id": "06", "name": "ext_map06_3wh_medium",
        "desc": "3 WH | 1 DA per WH cap=5 | 4 customers per warehouse",
        "mode": "multi_wh",
        "warehouses": [
            {"depot": WH0, "customers": [(10,25),(25,10),(5,40),(30,30)], "cap": 5},
            {"depot": WH1, "customers": [(75,10),(90,25),(85,35),(70,15)], "cap": 5},
            {"depot": WH2, "customers": [(40,85),(60,90),(50,70),(55,92)], "cap": 5},
        ],
        "gt": 215.4335,
    },
    {
        "id": "07", "name": "ext_map07_centralized",
        "desc": "3 WH CENTRALIZED | 3 DAs cap=5 all at WH-0 | 12 customers",
        "mode": "single_wh", "depot": WH0,
        "customers": [(15,15),(85,15),(50,85),(30,50),(70,50),(50,30),(20,70),(80,70),(50,50),(35,20),(65,20),(50,60)],
        "caps": [5, 5, 5], "gt": 410.7475,
        "n_partitions": 250866, "n_orderings": "per-group",
    },
    {
        "id": "08", "name": "ext_map08_outlier",
        "desc": "3 WH | 1 DA per WH | WH-0 has extreme outlier at (2,98)",
        "mode": "multi_wh",
        "warehouses": [
            {"depot": WH0, "customers": [(15,15),(25,20),(10,30),(2,98)], "cap": 5},
            {"depot": WH1, "customers": [(75,15),(85,20),(90,30)],        "cap": 3},
            {"depot": WH2, "customers": [(45,80),(55,85),(50,92)],        "cap": 3},
        ],
        "gt": 254.9191,
    },
]


# ── Runner ────────────────────────────────────────────────────────────────────

def run():
    print("=" * 72)
    print("  Extension VRP — Brute Force Optimality Proof")
    print("  Every valid route combination is enumerated per map.")
    print("=" * 72)

    summary = []

    for m in MAPS:
        print(f"\nMap {m['id']}: {m['name']}")
        print(f"  {m['desc']}")
        t0 = time.time()

        if m["mode"] == "single_wh":
            asgn, cost, n_parts = brute_force_multi_da(
                m["customers"], m["caps"], m["depot"])
            elapsed = time.time() - t0
            match = abs(cost - m["gt"]) < 0.02

            print(f"  Depot          : {m['depot']}")
            print(f"  Partitions     : {n_parts:,}")
            print(f"  Time           : {elapsed:.3f}s")
            print(f"  Optimal routes :")
            for i, route in enumerate(asgn):
                c     = route_dist(route, m["depot"])
                stops = " -> ".join([f"({p[0]},{p[1]})" for p in route])
                print(f"    DA{i+1}: Depot{m['depot']} -> {stops} -> Depot  [dist={c:.4f}]")
            print(f"  Solver total   : {cost:.4f}")
            print(f"  Ground truth   : {m['gt']}")
            print(f"  Verdict        : {'PROVEN GLOBAL OPTIMUM' if match else 'MISMATCH (check gt)'}")
            summary.append((m["id"], m["name"], m["gt"], cost, match))

        else:  # multi_wh
            total      = 0.0
            total_ord  = 0
            wh_results = []
            for wi, wh in enumerate(m["warehouses"]):
                route, cost, n_ord = brute_force_single_da(
                    wh["customers"], wh["depot"])
                total     += cost
                total_ord += n_ord
                wh_results.append((wi, wh["depot"], route, cost))

            elapsed = time.time() - t0
            match = abs(total - m["gt"]) < 0.02

            print(f"  Mode           : Per-warehouse single-DA permutation")
            print(f"  Total orderings: {total_ord:,}")
            print(f"  Time           : {elapsed:.3f}s")
            print(f"  Optimal routes :")
            for wi, depot, route, cost in wh_results:
                stops = " -> ".join([f"({p[0]},{p[1]})" for p in route])
                print(f"    DA{wi+1}(WH-{wi}): Depot{depot} -> {stops} -> Depot  [dist={cost:.4f}]")
            print(f"  Solver total   : {total:.4f}")
            print(f"  Ground truth   : {m['gt']}")
            print(f"  Verdict        : {'PROVEN GLOBAL OPTIMUM' if match else 'MISMATCH (check gt)'}")
            summary.append((m["id"], m["name"], m["gt"], total, match))

    print("\n" + "=" * 72)
    print("  SUMMARY")
    print("=" * 72)
    print(f"  {'Map':<4} {'Name':<32} {'GT':>10} {'Solver':>10}  Result")
    print(f"  {'-'*4} {'-'*32} {'-'*10} {'-'*10}  ------")
    for r in summary:
        status = "PASS" if r[4] else "FAIL"
        print(f"  {r[0]:<4} {r[1]:<32} {r[2]:>10.4f} {r[3]:>10.4f}  [{status}]")
    all_pass = all(r[4] for r in summary)
    print("=" * 72)
    print(f"  RESULT: {'ALL 8 MAPS — PROVEN GLOBAL OPTIMUM' if all_pass else 'SOME FAILED'}")
    print("=" * 72)


if __name__ == "__main__":
    run()
