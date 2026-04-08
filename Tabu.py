import sys
import random
import math
import copy
import matplotlib.pyplot as plt

# --- CONFIGURATION ---
WAREHOUSE = (50, 50)
MAP_SIZE = 100
DEFAULT_CUSTOMERS = 20
DEFAULT_ITERATIONS = 800
DEFAULT_TABU_TENURE = 15
random.seed()
LOCATIONS = [(random.randint(0, MAP_SIZE), random.randint(0, MAP_SIZE), 1) for _ in range(DEFAULT_CUSTOMERS)]


def calculate_distance(p1, p2):
    return math.sqrt((p1[0] - p2[0]) ** 2 + (p1[1] - p2[1]) ** 2)


def load_locations(num_customers, file_path):
    if file_path != "RANDOM":
        locations = []
        try:
            with open(file_path, "r", encoding="utf-8") as file_handle:
                for line in file_handle:
                    if line.strip():
                        x, y, demand = map(int, line.strip().split(","))
                        locations.append((x, y, demand))

            print(f"Loaded {len(locations)} customers from file.", flush=True)
            return locations
        except Exception as exc:
            print(f"Error reading file: {exc}. Falling back to random.", flush=True)

    return [
        (random.randint(0, MAP_SIZE), random.randint(0, MAP_SIZE), 1)
        for _ in range(num_customers)
    ]


class TabuVRP:
    def __init__(self, locations, capacities, agent_names):
        self.locations = locations
        self.num_customers = len(locations)
        self.capacities = capacities
        self.agent_names = agent_names
        self.customer_ids = list(range(self.num_customers))

    def calculate_cost(self, sequence):
        total_dist = 0
        current_pos = WAREHOUSE
        current_load = 0
        v_idx = 0

        for cust_idx in sequence:
            demand = self.locations[cust_idx][2]
            limit = self.capacities[min(v_idx, len(self.capacities) - 1)]

            if current_load + demand > limit:
                total_dist += calculate_distance(current_pos, WAREHOUSE)
                current_pos = WAREHOUSE
                current_load = 0

                if v_idx < len(self.agent_names) - 1:
                    v_idx += 1
                else:
                    total_dist += 99999

            total_dist += calculate_distance(current_pos, self.locations[cust_idx][:2])
            current_pos = self.locations[cust_idx][:2]
            current_load += demand

        total_dist += calculate_distance(current_pos, WAREHOUSE)
        return total_dist

    def run(self, iterations=DEFAULT_ITERATIONS, tabu_tenure=DEFAULT_TABU_TENURE):
        print("Starting Tabu Search optimization...", flush=True)

        current_sol = random.sample(self.customer_ids, len(self.customer_ids))
        best_sol = copy.deepcopy(current_sol)
        best_cost = self.calculate_cost(best_sol)
        tabu_list = {}

        for iteration in range(iterations):
            neighbors = []
            num_swaps = min(40, len(self.customer_ids) * (len(self.customer_ids) - 1) // 2)

            if num_swaps < 1:
                num_swaps = 1

            for _ in range(num_swaps):
                idx1, idx2 = random.sample(range(self.num_customers), 2)
                neighbor = copy.deepcopy(current_sol)
                neighbor[idx1], neighbor[idx2] = neighbor[idx2], neighbor[idx1]
                move = tuple(sorted((idx1, idx2)))
                neighbors.append((neighbor, move))

            best_neighbor = None
            best_neighbor_cost = float("inf")
            chosen_move = None

            for neighbor, move in neighbors:
                cost = self.calculate_cost(neighbor)

                if move not in tabu_list or cost < best_cost:
                    if cost < best_neighbor_cost:
                        best_neighbor_cost = cost
                        best_neighbor = neighbor
                        chosen_move = move

            if best_neighbor is not None:
                current_sol = best_neighbor
                tabu_list[chosen_move] = tabu_tenure

                if best_neighbor_cost < best_cost:
                    best_cost = best_neighbor_cost
                    best_sol = copy.deepcopy(best_neighbor)

            tabu_list = {move: tenure - 1 for move, tenure in tabu_list.items() if tenure > 1}

            if iteration % 100 == 0:
                print(f"Iteration {iteration:03d} | Best Distance: {best_cost:.2f}", flush=True)

        print(f"Iteration {iterations} | Final Best Distance: {best_cost:.2f}", flush=True)
        return best_sol


def plot_and_format_result(best_sequence, locations, agent_names, capacities, show_gui=True):
    plt.figure(figsize=(10, 7))
    plt.plot(WAREHOUSE[0], WAREHOUSE[1], "rs", markersize=12, label="Warehouse", zorder=5)

    for idx, (x, y, _demand) in enumerate(locations):
        plt.scatter(x, y, c="blue", alpha=0.6, s=30, zorder=4)
        plt.text(x + 1, y + 1, f"C{idx + 1}", fontsize=8)

    colors = ["#1f77b4", "#ff7f0e", "#2ca02c", "#d62728", "#9467bd", "#8c564b"]
    output_parts = []
    v_idx = 0
    current_load = 0
    path_x = [WAREHOUSE[0]]
    path_y = [WAREHOUSE[1]]
    route_indices = ["0"]

    def draw_route(path_points, color, label):
        xs = [point[0] for point in path_points]
        ys = [point[1] for point in path_points]
        if len(path_points) > 1:
            solid_points = path_points[:-1]
            if len(solid_points) > 1:
                solid_x = [point[0] for point in solid_points]
                solid_y = [point[1] for point in solid_points]
                plt.plot(solid_x, solid_y, color=color, linewidth=1.2, alpha=0.35)
                plt.plot(solid_x, solid_y, color=color, linewidth=2, label=label)

            return_x = [path_points[-2][0], path_points[-1][0]]
            return_y = [path_points[-2][1], path_points[-1][1]]
            plt.plot(return_x, return_y, color=color, linewidth=2, linestyle=":")
        else:
            plt.plot(xs, ys, color=color, linewidth=2, label=label)

        for index, (start, end) in enumerate(zip(path_points, path_points[1:])):
            linestyle = ":" if index == len(path_points) - 2 else "-"
            plt.annotate(
                "",
                xy=end,
                xytext=start,
                arrowprops=dict(arrowstyle="->", color=color, lw=1.8, linestyle=linestyle, shrinkA=0, shrinkB=0),
            )

    for cust_idx in best_sequence:
        loc = locations[cust_idx]
        limit = capacities[min(v_idx, len(capacities) - 1)]

        if current_load + loc[2] > limit:
            path_x.append(WAREHOUSE[0])
            path_y.append(WAREHOUSE[1])
            route_indices.append("0")

            agent_name = agent_names[min(v_idx, len(agent_names) - 1)]
            route_points = list(zip(path_x, path_y))
            draw_route(route_points, colors[v_idx % len(colors)], f"{agent_name} (Cap: {limit})")
            output_parts.append(f"{agent_name}:{','.join(route_indices)}")

            if v_idx < len(agent_names) - 1:
                v_idx += 1
            else:
                print(f"DEBUG: Agent {agent_name} forced to take additional load!", flush=True)

            current_load = 0
            path_x = [WAREHOUSE[0]]
            path_y = [WAREHOUSE[1]]
            route_indices = ["0"]

        path_x.append(loc[0])
        path_y.append(loc[1])
        route_indices.append(str(cust_idx + 1))
        current_load += loc[2]

    # Handle final used vehicle path
    path_x.append(WAREHOUSE[0])
    path_y.append(WAREHOUSE[1])
    route_indices.append("0")
    agent_name = agent_names[min(v_idx, len(agent_names) - 1)]
    route_points = list(zip(path_x, path_y))
    draw_route(
        route_points,
        colors[v_idx % len(colors)],
        f"{agent_name} (Cap: {capacities[min(v_idx, len(capacities) - 1)]})",
    )
    output_parts.append(f"{agent_name}:{','.join(route_indices)}")

    for i in range(v_idx + 1, len(agent_names)):
        standby_agent = agent_names[i]
        standby_cap = capacities[i]
        standby_color = colors[i % len(colors)]

        plt.plot([], [], color=standby_color, linewidth=2, linestyle=':', label=f"{standby_agent} (Standby Cap: {standby_cap})")
        output_parts.append(f"{standby_agent}:0")

    total_distance = TabuVRP(locations, capacities, agent_names).calculate_cost(best_sequence)
    plt.title(f"MRA Optimized Delivery Plan (Tabu)\nTotal Logistics Distance: {total_distance:.2f}")
    plt.legend(loc="upper left", bbox_to_anchor=(1, 1))
    plt.grid(True, linestyle=":", alpha=0.6)
    plt.tight_layout()

    final_output = "|".join(output_parts)
    print("FINAL_ROUTES:" + final_output, flush=True)

    if show_gui:
        plt.show(block=True)
    else:
        plt.close()

    return final_output


if __name__ == "__main__":
    if len(sys.argv) > 4:
        names = sys.argv[1].split(",")
        caps = [int(cap) for cap in sys.argv[2].split(",")]
        target_customer_count = int(sys.argv[3])
        file_path = sys.argv[4]

        LOCATIONS = load_locations(target_customer_count, file_path)
        solver = TabuVRP(LOCATIONS, caps, names)
        best_sequence = solver.run(iterations=DEFAULT_ITERATIONS, tabu_tenure=DEFAULT_TABU_TENURE)
        plot_and_format_result(best_sequence, LOCATIONS, names, caps, show_gui=True)
    else:
        debug_names = ["Vehicle1", "Vehicle2", "Vehicle3"]
        debug_capacities = [7, 7, 7]
        LOCATIONS = load_locations(12, "RANDOM")
        solver = TabuVRP(LOCATIONS, debug_capacities, debug_names)
        best_sequence = solver.run(iterations=120, tabu_tenure=8)
        plot_and_format_result(best_sequence, LOCATIONS, debug_names, debug_capacities, show_gui=True)