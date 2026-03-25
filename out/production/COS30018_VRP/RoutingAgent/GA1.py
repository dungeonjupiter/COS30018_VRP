import random
import numpy as np
import matplotlib.pyplot as plt

# --- CONFIGURATION & DATA ---
# Warehouse is now centered at (50, 50)
WAREHOUSE = (50, 50)
NUM_CUSTOMERS = 20
MAP_SIZE = 100

# Generate 20 random customers: (x, y, demand)
random.seed(100) # For consistent results
LOCATIONS = [
    (random.randint(0, MAP_SIZE), random.randint(0, MAP_SIZE), 1) 
    for _ in range(NUM_CUSTOMERS)
]

VEHICLE_CAPACITY = 5 # Each vehicle carries 5 items
POPULATION_SIZE = 100
GENERATIONS = 200
MUTATION_RATE = 0.2

def calculate_distance(p1, p2):
    return np.sqrt((p1[0] - p2[0])**2 + (p1[1] - p2[1])**2)

# --- GA CORE LOGIC ---

def fitness(chromosome):
    total_distance = 0
    current_load = 0
    current_pos = WAREHOUSE
    
    for idx in chromosome:
        loc = LOCATIONS[idx]
        demand = loc[2]
        if current_load + demand > VEHICLE_CAPACITY:
            total_distance += calculate_distance(current_pos, WAREHOUSE)
            current_pos = WAREHOUSE
            current_load = 0
        total_distance += calculate_distance(current_pos, (loc[0], loc[1]))
        current_pos = (loc[0], loc[1])
        current_load += demand
        
    total_distance += calculate_distance(current_pos, WAREHOUSE)
    return 1 / total_distance

def ordered_crossover(p1, p2):
    size = len(p1)
    a, b = sorted(random.sample(range(size), 2))
    child = [-1] * size
    child[a:b] = p1[a:b]
    p2_filtered = [item for item in p2 if item not in child]
    idx = 0
    for i in range(size):
        if child[i] == -1:
            child[i] = p2_filtered[idx]
            idx += 1
    return child

def mutate(chromosome):
    if random.random() < MUTATION_RATE:
        i, j = random.sample(range(len(chromosome)), 2)
        chromosome[i], chromosome[j] = chromosome[j], chromosome[i]

# --- VISUALIZATION ---

def plot_routes(chromosome):
    plt.figure(figsize=(12, 8))
    
    # 1. Plot Warehouse and Customers first
    plt.plot(WAREHOUSE[0], WAREHOUSE[1], 'rs', markersize=15, label='Main Warehouse', zorder=5)
    xs = [loc[0] for loc in LOCATIONS]
    ys = [loc[1] for loc in LOCATIONS]
    plt.scatter(xs, ys, c='black', alpha=0.5, s=30, zorder=4)
    for i, (x, y, d) in enumerate(LOCATIONS):
        plt.text(x+1, y+1, f'C{i}', fontsize=8, fontweight='bold')

    # 2. Logic to track routes and build legend
    colors = ['#1f77b4', '#ff7f0e', '#2ca02c', '#d62728', '#9467bd', '#8c564b', '#e377c2', '#7f7f7f']
    current_pos = WAREHOUSE
    current_load = 0
    vehicle_id = 1
    
    # We will store the current vehicle's path to plot at once
    path_x = [WAREHOUSE[0]]
    path_y = [WAREHOUSE[1]]

    for idx in chromosome:
        loc = LOCATIONS[idx]
        demand = loc[2]
        
        if current_load + demand > VEHICLE_CAPACITY:
            # Finish current vehicle path by returning to warehouse
            path_x.append(WAREHOUSE[0])
            path_y.append(WAREHOUSE[1])
            
            # Plot the completed route for this vehicle
            plt.plot(path_x, path_y, color=colors[(vehicle_id-1) % len(colors)], 
                     linewidth=2.5, label=f'Vehicle {vehicle_id} ({current_load} items)', zorder=3)
            
            # Reset for next vehicle
            vehicle_id += 1
            current_load = 0
            path_x = [WAREHOUSE[0]]
            path_y = [WAREHOUSE[1]]
        
        path_x.append(loc[0])
        path_y.append(loc[1])
        current_load += demand

    # Plot the very last vehicle's route
    path_x.append(WAREHOUSE[0])
    path_y.append(WAREHOUSE[1])
    plt.plot(path_x, path_y, color=colors[(vehicle_id-1) % len(colors)], 
             linewidth=2.5, label=f'Vehicle {vehicle_id} ({current_load} items)', zorder=3)

    # 3. Formatting the Plot
    plt.title(f"MRA Optimized Delivery Plan\nTotal Logistics Distance: {1/fitness(chromosome):.2f}", fontsize=14)
    plt.xlabel("X Coordinate (km)")
    plt.ylabel("Y Coordinate (km)")
    
    # Place legend outside the plot so it doesn't cover the routes
    plt.legend(loc='upper left', bbox_to_anchor=(1, 1), title="Route Assignments", fontsize='small')
    
    plt.grid(True, linestyle='--', alpha=0.7)
    plt.tight_layout() # Adjust layout to make room for legend
    plt.show()

# --- RUN ---
pop = [list(range(len(LOCATIONS))) for _ in range(POPULATION_SIZE)]
for p in pop: random.shuffle(p)

for gen in range(GENERATIONS):
    pop = sorted(pop, key=lambda x: fitness(x), reverse=True)
    next_gen = pop[:10] # Elitism
    while len(next_gen) < POPULATION_SIZE:
        p1, p2 = random.sample(pop[:30], 2)
        child = ordered_crossover(p1, p2)
        mutate(child)
        next_gen.append(child)
    pop = next_gen
    if gen % 50 == 0:
        print(f"Gen {gen} | Best Dist: {1/fitness(pop[0]):.2f}")

plot_routes(pop[0])