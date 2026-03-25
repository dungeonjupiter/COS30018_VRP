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

    for cust_idx in best_chromosome:
        loc = LOCATIONS[cust_idx]
        limit = capacities[min(v_idx, len(capacities)-1)]

        if current_load + loc[2] > limit:
            # Close loop to warehouse
            path_x.append(WAREHOUSE[0]); path_y.append(WAREHOUSE[1])
            route_indices.append("0")

            aname = agent_names[min(v_idx, len(agent_names)-1)]
            plt.plot(path_x, path_y, color=colors[v_idx % len(colors)], linewidth=2,
                     label=f"{aname} (Cap: {limit})")
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
    plt.plot(path_x, path_y, color=colors[v_idx % len(colors)], linewidth=2,
             label=f"{aname} (Cap: {capacities[min(v_idx, len(capacities)-1)]})")
    output_parts.append(f"{aname}:{','.join(route_indices)}")

    plt.title(f"MRA Optimized Delivery Plan\nTotal Logistics Distance: {1/fitness(best_chromosome, capacities):.2f}")
    plt.legend(loc='upper left', bbox_to_anchor=(1, 1))
    plt.grid(True, linestyle=':', alpha=0.6)
    plt.tight_layout()
    plt.show(block=True)
    return "|".join(output_parts)

if __name__ == "__main__":
    if len(sys.argv) > 2:
        names = sys.argv[1].split(',')
        caps = [int(c) for c in sys.argv[2].split(',')]

        pop_size = 200
        generations = 800
        MUTATION_RATE = 0.4  # High mutation rate helps break out of messy lines

        pop = [list(range(NUM_CUSTOMERS)) for _ in range(pop_size)]

        for _ in range(generations):
            pop = sorted(pop, key=lambda x: fitness(x, caps), reverse=True)
            next_gen = pop[:10] # Elitism

            while len(next_gen) < pop_size:
                p1, p2 = random.sample(pop[:50], 2)
                child = ordered_crossover(p1, p2)

                # --- THIS IS THE CRITICAL MISSING MUTATION STEP ---
                if random.random() < MUTATION_RATE:
                    idx1, idx2 = random.sample(range(NUM_CUSTOMERS), 2)
                    child[idx1], child[idx2] = child[idx2], child[idx1]
                # --------------------------------------------------

                next_gen.append(child)

            pop = next_gen

        print(plot_and_format_result(pop[0], names, caps))