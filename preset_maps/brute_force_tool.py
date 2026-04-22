import math
import itertools
import time

DEPOT = (50, 50)

def euclidean(a, b):
    return math.sqrt((a[0] - b[0])**2 + (a[1] - b[1])**2)

def route_dist(route, customers):
    if not route:
        return 0.0
    d = euclidean(DEPOT, customers[route[0]][:2])
    for i in range(len(route) - 1):
        d += euclidean(customers[route[i]][:2], customers[route[i+1]][:2])
    d += euclidean(customers[route[-1]][:2], DEPOT)
    return d

def held_karp(group, customers):
    """
    Held-Karp DP: finds the optimal visiting order
    For large group
    Time complexity: O(n^2 * 2^n)
    """
    if not group:
        return 0.0, []
    if len(group) == 1:
        return euclidean(DEPOT, customers[group[0]][:2]) * 2, list(group)

    n = len(group)
    nodes = list(group)

    dm = [[0.0] * (n + 1) for _ in range(n + 1)]
    for i in range(n):
        for j in range(n):
            dm[i][j] = euclidean(customers[nodes[i]][:2], customers[nodes[j]][:2])
        dm[i][n] = euclidean(customers[nodes[i]][:2], DEPOT)
        dm[n][i] = euclidean(DEPOT, customers[nodes[i]][:2])

    INF = float('inf')
    dp   = [[INF] * n for _ in range(1 << n)]
    prev = [[-1]  * n for _ in range(1 << n)]

    for i in range(n):
        dp[1 << i][i] = dm[n][i]

    for S in range(1, 1 << n):
        for i in range(n):
            if not (S >> i & 1) or dp[S][i] == INF:
                continue
            for j in range(n):
                if S >> j & 1:
                    continue
                nS = S | (1 << j)
                nc = dp[S][i] + dm[i][j]
                if nc < dp[nS][j]:
                    dp[nS][j] = nc
                    prev[nS][j] = i

    full = (1 << n) - 1
    best_c, last = INF, -1
    for i in range(n):
        c = dp[full][i] + dm[i][n]
        if c < best_c:
            best_c, last = c, i

    path = []
    S, cur = full, last
    while cur != -1:
        path.append(nodes[cur])
        nxt = prev[S][cur]
        S ^= (1 << cur)
        cur = nxt
    path.reverse()
    return best_c, path

def gen_partitions(remaining, num_groups, caps, demands):
    if num_groups == 1:
        if sum(demands[i] for i in remaining) <= caps[0]:
            yield [list(remaining)]
        return
    for size in range(0, len(remaining) + 1):
        for group in itertools.combinations(remaining, size):
            if sum(demands[i] for i in group) > caps[0]:
                continue
            rest = [x for x in remaining if x not in group]
            for rest_part in gen_partitions(rest, num_groups - 1, caps[1:], demands):
                yield [list(group)] + rest_part

def solve_vrp_brute(map_name, customers, caps, known_gt):
    n = len(customers)
    num_da = len(caps)
    demands = [c[2] for c in customers]

    best_cost = float('inf')
    best_assignment = None
    partitions_checked = 0
    orderings_checked = 0

    for partition in gen_partitions(list(range(n)), num_da, caps, demands):
        partitions_checked += 1
        total = 0.0
        routes = []

        for group in partition:
            if len(group) <= 7:
                # Full permutation brute force
                best_group_cost = float('inf')
                best_group_route = list(group)
                for perm in itertools.permutations(group):
                    orderings_checked += 1
                    d = route_dist(list(perm), customers)
                    if d < best_group_cost:
                        best_group_cost = d
                        best_group_route = list(perm)
                total += best_group_cost
                routes.append(best_group_route)
            else:
                # Held-Karp exact DP for larger groups
                d, path = held_karp(group, customers)
                total += d
                routes.append(path)

        if total < best_cost:
            best_cost = total
            best_assignment = routes

    return best_assignment, best_cost, partitions_checked, orderings_checked

MAPS = [
    {
        "id": "01", "name": "map01_tiny_compass",
        "customers": [(50,90,1),(90,50,1),(50,10,1),(10,50,1)],
        "caps": [4], "gt": 249.71
    },
    {
        "id": "02", "name": "map02_two_clusters",
        "customers": [(10,80,1),(15,85,1),(5,75,1),(20,80,1),(85,15,1),(90,20,1),(80,10,1),(90,10,1)],
        "caps": [4,4], "gt": 239.26
    },
    {
        "id": "03", "name": "map03_three_sectors",
        "customers": [(10,90,1),(5,80,1),(20,85,1),(90,90,1),(85,80,1),(80,85,1),(50,5,1),(40,10,1),(60,8,1)],
        "caps": [3,3,3], "gt": 343.12
    },
    {
        "id": "04", "name": "map04_medium_balanced",
        "customers": [(10,10,1),(20,30,1),(30,10,1),(10,30,1),(25,20,1),(15,50,1),
                      (70,70,1),(80,90,1),(90,70,1),(70,90,1),(85,75,1),(75,55,1)],
        "caps": [6,6], "gt": 281.91
    },
    {
        "id": "05", "name": "map05_linear_street",
        "customers": [(5,50,1),(25,50,1),(40,50,1),(60,50,1),(75,50,1),(95,50,1)],
        "caps": [3,3], "gt": 180.00
    },
    {
        "id": "06", "name": "map06_tight_cluster",
        "customers": [(3,3,1),(5,7,1),(8,2,1),(2,8,1),(6,5,1),(9,9,1),(4,6,1),(7,4,1)],
        "caps": [4,4], "gt": 266.11
    },
    {
        "id": "07", "name": "map07_outlier",
        "customers": [(45,55,1),(55,45,1),(40,40,1),(60,60,1),(50,30,1),(99,99,1)],
        "caps": [3,3], "gt": 197.19
    },
    {
        "id": "08", "name": "map08_one_sided",
        "customers": [(5,10,1),(10,30,1),(5,50,1),(15,70,1),(5,90,1),(20,50,1),(10,20,1),(20,40,1),(15,60,1)],
        "caps": [3,3,3], "gt": 349.73
    },
    {
        "id": "09", "name": "map09_star_pattern",
        "customers": [(50,85,1),(75,75,1),(85,50,1),(75,25,1),(50,15,1),(25,25,1),(15,50,1),(25,75,1)],
        "caps": [4,4], "gt": 302.27
    },
    {
        "id": "10", "name": "map10_unequal_demand",
        "customers": [(10,10,3),(30,20,2),(20,40,1),(70,30,2),(80,60,3),(60,80,1),
                      (30,70,2),(50,20,1),(40,60,2),(90,90,3)],
        "caps": [7,7,7], "gt": 375.23
    },
]

if __name__ == "__main__":
    print("=" * 70)
    print("  VRP Brute Force Optimality Proof Tool")
    print("  Enumerates every valid route combination to find proven global optimum")
    print("=" * 70)

    summary = []

    for m in MAPS:
        customers = m["customers"]
        caps      = m["caps"]
        gt        = m["gt"]

        print(f"\nMap {m['id']}: {m['name']}")
        print(f"  Customers={len(customers)}, DAs={len(caps)}, Caps={caps}")
        t0 = time.time()

        assignment, cost, n_partitions, n_orderings = solve_vrp_brute(
            m["name"], customers, caps, gt
        )
        elapsed = time.time() - t0
        match = abs(cost - gt) < 0.02

        print(f"  Partitions checked : {n_partitions:,}")
        print(f"  Orderings checked  : {n_orderings:,}")
        print(f"  Time               : {elapsed:.3f}s")
        print(f"  Optimal routes:")
        for i, route in enumerate(assignment):
            cap  = caps[i] if i < len(caps) else caps[-1]
            load = sum(customers[c][2] for c in route)
            stops = " -> ".join([f"C{c+1}({customers[c][0]},{customers[c][1]})" for c in route])
            d = route_dist(route, customers)
            print(f"    DA{i+1} (load={load}/{cap}): Depot -> {stops} -> Depot  [dist={d:.4f}]")
        print(f"  Solver total  : {cost:.4f}")
        print(f"  Ground truth  : {gt}")
        print(f"  Verdict       : {'PROVEN GLOBAL OPTIMUM' if match else f'MISMATCH (got {cost:.4f})'}")
        summary.append((m['id'], m['name'], gt, cost, n_partitions, n_orderings, elapsed, match))

    print("\n" + "=" * 70)
    print("  SUMMARY")
    print("=" * 70)
    print(f"  {'Map':<4} {'Name':<28} {'GT':>8} {'Result':>9} {'Parts':>8} {'Orders':>12} {'Time':>7} {'OK'}")
    print(f"  {'-'*4} {'-'*28} {'-'*8} {'-'*9} {'-'*8} {'-'*12} {'-'*7} {'-'*4}")
    for r in summary:
        orders_str = f"{r[5]:,}" if r[5] > 0 else "HK-DP"
        print(f"  {r[0]:<4} {r[1]:<28} {r[2]:>8} {r[3]:>9.4f} {r[4]:>8,} {orders_str:>12} {r[6]:>6.2f}s {'T' if r[7] else 'F'}")
    all_pass = all(r[7] for r in summary)
    print("=" * 70)
    print(f"  RESULT: {'ALL 10 MAPS — PROVEN GLOBAL OPTIMUM' if all_pass else 'SOME MAPS FAILED'}")
    print("=" * 70)
