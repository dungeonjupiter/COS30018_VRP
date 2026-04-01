import sys
import random
import math
import copy
import matplotlib.pyplot as plt

# --- 1. CONFIGURATION ---
WAREHOUSE = (50, 50)
MAP_SIZE = 100
random.seed() # Keeps seed dynamic, matching GA.py behavior

def calculate_distance(p1, p2):
    return math.sqrt((p1[0] - p2[0])**2 + (p1[1] - p2[1])**2)

# --- 2. TABU SEARCH ENGINE ---
class TabuVRP:
    def __init__(self, num_customers, capacities, agent_names):
        self.num_customers = num_customers
        self.capacities = capacities
        self.agent_names = agent_names

        # Generate random map locations (x, y, demand=1)
        self.locations = [(random.randint(0, MAP_SIZE), random.randint(0, MAP_SIZE), 1) for _ in range(num_customers)]
        self.customer_ids = list(range(num_customers))

    def calculate_cost(self, sequence):
        total_dist = 0
        current_pos = WAREHOUSE
        v_idx = 0
        current_load = 0
        routes = {name: [] for name in self.agent_names}

        for cust_idx in sequence:
            demand = self.locations[cust_idx][2]
            # Safely get capacity for the current vehicle
            limit = self.capacities[min(v_idx, len(self.capacities)-1)]

            # If adding this customer exceeds the vehicle's capacity
            if current_load + demand > limit:
                total_dist += calculate_distance(current_pos, WAREHOUSE) # Return to warehouse
                current_pos = WAREHOUSE
                current_load = 0
                v_idx += 1

                # Apply heavy penalty if we run out of vehicles
                if v_idx >= len(self.agent_names):
                    total_dist += 99999
                    break

            total_dist += calculate_distance(current_pos, self.locations[cust_idx][:2])
            routes[self.agent_names[min(v_idx, len(self.agent_names)-1)]].append(cust_idx)
            current_pos = self.locations[cust_idx][:2]
            current_load += demand

        # Last vehicle returns to warehouse
        total_dist += calculate_distance(current_pos, WAREHOUSE)
        return total_dist, routes

    def run(self, iterations=800, tabu_tenure=15):
        # flush=True pushes the log instantly to the JADE console
        print("Starting Tabu Search optimization...", flush=True)

        current_sol = random.sample(self.customer_ids, len(self.customer_ids))
        best_sol = copy.deepcopy(current_sol)
        best_cost, _ = self.calculate_cost(best_sol)
        tabu_list = {}

        for i in range(iterations):
            neighbors = []

            # Generate neighborhood subset (keeps execution fast)
            num_swaps = min(40, len(self.customer_ids) * (len(self.customer_ids) - 1) // 2)
            if num_swaps < 1: num_swaps = 1

            for _ in range(num_swaps):
                idx1, idx2 = random.sample(range(self.num_customers), 2)
                neighbor = copy.deepcopy(current_sol)
                neighbor[idx1], neighbor[idx2] = neighbor[idx2], neighbor[idx1]
                move = tuple(sorted((idx1, idx2)))
                neighbors.append((neighbor, move))

            best_neighbor = None
            best_neighbor_cost = float('inf')
            chosen_move = None

            for neighbor, move in neighbors:
                cost, _ = self.calculate_cost(neighbor)

                # Aspiration criterion: accept Tabu move if it yields a global best
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

            # Decay tabu tenure
            tabu_list = {m: t-1 for m, t in tabu_list.items() if t > 1}

            # Mimic GA's progress logging for the Java console
            if i % 100 == 0:
                print(f"Iteration {i:03d} | Best Distance: {best_cost:.2f}", flush=True)

        return best_sol

    # --- Temporary visualisation ---
def plot_routes(locations, routes, warehouse):
    plt.figure(figsize=(8, 8))

    # 1. Plot Warehouse
    plt.plot(warehouse[0], warehouse[1], 'rs', markersize=10, label='Warehouse')

    # 2. Plot Customers
    customer_x = [loc[0] for loc in locations]
    customer_y = [loc[1] for loc in locations]
    plt.scatter(customer_x, customer_y, c='blue', label='Customers')

    # Add labels to customers
    for i, (x, y, _) in enumerate(locations):
        plt.text(x + 1, y + 1, str(i), fontsize=9)

    # 3. Plot Routes for each agent
    colors = ['green', 'orange', 'purple', 'cyan', 'brown']
    for i, (agent, path) in enumerate(routes.items()):
        if not path: continue

        # Color for this agent
        color = colors[i % len(colors)]

        # Start from Warehouse
        curr_x, curr_y = warehouse
        for node_idx in path:
            next_x, next_y = locations[node_idx][0], locations[node_idx][1]
            plt.annotate('', xy=(next_x, next_y), xytext=(curr_x, curr_y),
                         arrowprops=dict(arrowstyle='->', color=color, lw=1.5))
            curr_x, curr_y = next_x, next_y

        # Return to Warehouse
        plt.annotate('', xy=(warehouse[0], warehouse[1]), xytext=(curr_x, curr_y),
                     arrowprops=dict(arrowstyle='->', color=color, lw=1.5, ls='--'))

    plt.title("Tabu Search Optimized Routes")
    plt.xlabel("X Coordinate")
    plt.ylabel("Y Coordinate")
    plt.legend()
    plt.grid(True)
    plt.show() # This will pause the script until you close the window


# --- 3. JADE COMMUNICATION BRIDGE ---
if __name__ == "__main__":
    # Java passes 4 arguments: namesArg, capsArg, targetCustomerCount, dataFile
    if len(sys.argv) > 3:
        try:
            # 1. Parse arguments from Java
            agent_names = sys.argv[1].split(',')
            capacities = [int(c) for c in sys.argv[2].split(',')]
            num_customers = int(sys.argv[3])
            # data_file = sys.argv[4] # Captured but not used in this basic random version

            # 2. Initialize and run Tabu Search solver
            solver = TabuVRP(num_customers, capacities, agent_names)
            best_chromosome = solver.run(iterations=800, tabu_tenure=15)
            _, final_routes = solver.calculate_cost(best_chromosome)

            # 3. Format output strictly for Java parsing
            # Your Java MRA expects: AgentName:node,node|AgentName:node...
            output_parts = []
            for agent in agent_names:
                route = final_routes.get(agent, [])
                route_str = ",".join(map(str, route))

                # To ensure Java's .split(":") results in length 2,
                # we only add agents that actually have tasks.
                if len(route) > 0:
                    output_parts.append(f"{agent}:{route_str}")

            final_output = "|".join(output_parts)

            # 4. Final Transmission with the REQUIRED Java Prefix
            # Java is looking for "FINAL_ROUTES:" to stop the loop and start parsing.
            print(f"FINAL_ROUTES:{final_output}", flush=True)

        except Exception as e:
            # If it fails, print the error so it shows up in the JADE console
            print(f"PYTHON_ERROR: {str(e)}", flush=True)
    else:
        print("PYTHON_ERROR: Missing arguments from MRA.", flush=True)