import sys
import random
import numpy as np
import matplotlib.pyplot as plt

# --- CONFIGURATION ---
WAREHOUSE = (50, 50)
MAP_SIZE = 100
random.seed() # Keep seed consistent for testing
NUM_CUSTOMERS = 20
LOCATIONS = [(random.randint(0, MAP_SIZE), random.randint(0, MAP_SIZE), 1) for _ in range(NUM_CUSTOMERS)]

def calculate_distance(p1, p2):
    return np.sqrt((p1[0] - p2[0])**2 + (p1[1] - p2[1])**2)

def fitness(chromosome, capacities):
    total_dist = 0
    current_pos = WAREHOUSE
    v_idx = 0
    current_load = 0
    for cust_idx in chromosome:
        demand = LOCATIONS[cust_idx][2]
        limit = capacities[min(v_idx, len(capacities)-1)]
        if current_load + demand > limit:
            total_dist += calculate_distance(current_pos, WAREHOUSE)
            current_pos = WAREHOUSE
            current_load = 0
            v_idx += 1
        total_dist += calculate_distance(current_pos, LOCATIONS[cust_idx][:2])
        current_pos = LOCATIONS[cust_idx][:2]
        current_load += demand
    total_dist += calculate_distance(current_pos, WAREHOUSE)
    return 1 / total_dist

def ordered_crossover(p1, p2):
    size = len(p1)
    a, b = sorted(random.sample(range(size), 2))
    child = [-1] * size
    child[a:b] = p1[a:b]
    remaining = [item for item in p2 if item not in child]
    idx = 0
    for i in range(size):
        if child[i] == -1:
            child[i] = remaining[idx]
            idx += 1
    return child

def plot_and_format_result(best_chromosome, agent_names, capacities):
    plt.figure(figsize=(10, 7))
    plt.plot(WAREHOUSE[0], WAREHOUSE[1], 'rs', markersize=12, label='Warehouse', zorder=5)

    # Plot customer locations with labels
    for i, (x, y, d) in enumerate(LOCATIONS):
        plt.scatter(x, y, c='blue', alpha=0.6, s=30, zorder=4)
        plt.text(x+1, y+1, f'C{i+1}', fontsize=8)

    colors = ['#1f77b4', '#ff7f0e', '#2ca02c', '#d62728', '#9467bd', '#8c564b']
    output_parts = []
    v_idx, current_load = 0, 0
    path_x, path_y = [WAREHOUSE[0]], [WAREHOUSE[1]]
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
            plt.plot(return_x, return_y, color=color, linewidth=2, linestyle=':')
        else:
            plt.plot(xs, ys, color=color, linewidth=2, label=label)

        for index, (start, end) in enumerate(zip(path_points, path_points[1:])):
            linestyle = ':' if index == len(path_points) - 2 else '-'
            plt.annotate(
                '',
                xy=end,
                xytext=start,
                arrowprops=dict(arrowstyle='->', color=color, lw=1.8, linestyle=linestyle, shrinkA=0, shrinkB=0),
            )

    for cust_idx in best_chromosome:
        loc = LOCATIONS[cust_idx]
        limit = capacities[min(v_idx, len(capacities)-1)]

        if current_load + loc[2] > limit:
            # Close loop to warehouse
            path_x.append(WAREHOUSE[0]); path_y.append(WAREHOUSE[1])
            route_indices.append("0")

            aname = agent_names[min(v_idx, len(agent_names)-1)]
            route_points = list(zip(path_x, path_y))
            draw_route(route_points, colors[v_idx % len(colors)], f"{aname} (Cap: {limit})")
            output_parts.append(f"{aname}:{','.join(route_indices)}")

            # CRITICAL FIX: Only move to next agent if available
            if v_idx < len(agent_names) - 1:
                v_idx += 1
            else:
                print(f"DEBUG: Agent {aname} forced to take additional load!")

            current_load = 0
            path_x, path_y = [WAREHOUSE[0]], [WAREHOUSE[1]]
            route_indices = ["0"]

        path_x.append(loc[0]); path_y.append(loc[1])
        route_indices.append(str(cust_idx + 1))
        current_load += loc[2]

    # Handle final vehicle path
    path_x.append(WAREHOUSE[0]); path_y.append(WAREHOUSE[1])
    route_indices.append("0")
    aname = agent_names[min(v_idx, len(agent_names)-1)]
    route_points = list(zip(path_x, path_y))
    draw_route(route_points, colors[v_idx % len(colors)], f"{aname} (Cap: {capacities[min(v_idx, len(capacities)-1)]})")
    output_parts.append(f"{aname}:{','.join(route_indices)}")

    plt.title(f"MRA Optimized Delivery Plan\nTotal Logistics Distance: {1/fitness(best_chromosome, capacities):.2f}")
    plt.legend(loc='upper left', bbox_to_anchor=(1, 1))
    plt.grid(True, linestyle=':', alpha=0.6)
    plt.tight_layout()

    final_output = "|".join(output_parts)

    print("FINAL_ROUTES:" + final_output, flush=True)

    plt.show(block=True)
    return final_output

if __name__ == "__main__":
    # Expecting 4 args: Names, Capacities, CustomerCount, FilePath
    if len(sys.argv) > 4:
        names = sys.argv[1].split(',')
        caps = [int(c) for c in sys.argv[2].split(',')]
        file_path = sys.argv[4]

        # --- FILE LOADING LOGIC ---
        if file_path != "RANDOM":
            LOCATIONS = []
            try:
                with open(file_path, 'r') as f:
                    for line in f:
                        if line.strip(): # Skip empty lines
                            x, y, d = map(int, line.strip().split(','))
                            LOCATIONS.append((x, y, d))
                NUM_CUSTOMERS = len(LOCATIONS)
                print(f"Loaded {NUM_CUSTOMERS} customers from file.", flush=True)
            except Exception as e:
                print(f"Error reading file: {e}. Falling back to random.", flush=True)
                NUM_CUSTOMERS = int(sys.argv[3])
                LOCATIONS = [(random.randint(0, MAP_SIZE), random.randint(0, MAP_SIZE), 1) for _ in range(NUM_CUSTOMERS)]
        else:
            NUM_CUSTOMERS = int(sys.argv[3])
            LOCATIONS = [(random.randint(0, MAP_SIZE), random.randint(0, MAP_SIZE), 1) for _ in range(NUM_CUSTOMERS)]

        # --- GA SETUP ---
        pop_size = 200
        generations = 800
        MUTATION_RATE = 0.4

        pop = [list(range(NUM_CUSTOMERS)) for _ in range(pop_size)]

        print("Starting Genetic Algorithm optimization...", flush=True)

        # --- EVOLUTION LOOP WITH TERMINAL OUTPUT ---
        for gen in range(generations):
            pop = sorted(pop, key=lambda x: fitness(x, caps), reverse=True)
            next_gen = pop[:10] # Elitism

            # Print progress every 100 generations
            if gen % 100 == 0:
                current_best_dist = 1 / fitness(pop[0], caps)
                # flush=True pushes this print directly to the Java console instantly
                print(f"Generation {gen:03d} | Best Distance: {current_best_dist:.2f}", flush=True)

            while len(next_gen) < pop_size:
                p1, p2 = random.sample(pop[:50], 2)
                child = ordered_crossover(p1, p2)

                if random.random() < MUTATION_RATE:
                    idx1, idx2 = random.sample(range(NUM_CUSTOMERS), 2)
                    child[idx1], child[idx2] = child[idx2], child[idx1]

                next_gen.append(child)

            pop = next_gen

        # Final generation output
        final_best_dist = 1 / fitness(pop[0], caps)
        print(f"Generation {generations} | Final Best Distance: {final_best_dist:.2f}", flush=True)

        plot_and_format_result(pop[0], names, caps)