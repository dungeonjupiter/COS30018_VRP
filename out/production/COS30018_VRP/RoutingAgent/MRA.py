"""
Master Routing Agent (MRA) - Genetic Algorithm Implementation
Solves the Capacitated Vehicle Routing Problem (CVRP)
"""

import math
import random
import copy

try:
    import matplotlib.pyplot as plt
    MATPLOTLIB_AVAILABLE = True
except ImportError:
    MATPLOTLIB_AVAILABLE = False
    print("Warning: matplotlib not found. Visualization will be skipped.")
    print("To enable visualization, run: pip install matplotlib")


# ==========================================
# PHASE 1: DATA STRUCTURES
# ==========================================

class Node:
    """Represents a physical location (Warehouse or Delivery Point)."""
    def __init__(self, node_id, x, y):
        self.id = node_id
        self.x = x
        self.y = y

    def __repr__(self):
        return f"Node({self.id})"

class Vehicle:
    """Represents a delivery agent with a maximum carrying capacity."""
    def __init__(self, vehicle_id, capacity):
        self.id = vehicle_id
        self.max_capacity = capacity

    def __repr__(self):
        return f"Vehicle({self.id}, cap={self.max_capacity})"


# ==========================================
# PHASE 2 & 3: THE MASTER ROUTING AGENT
# ==========================================

class MasterRoutingAgent:
    """
    The Brain of the routing system. Uses a Genetic Algorithm to find
    optimal routes for a fleet of vehicles.
    """
    def __init__(self, all_nodes, vehicles, warehouse_id=0):
        self.nodes = {node.id: node for node in all_nodes} # Dictionary for quick O(1) lookup
        self.vehicles = vehicles
        self.warehouse_id = warehouse_id
        self.distance_matrix = self._build_distance_matrix(all_nodes)
        
        # Extract just the delivery IDs (exclude warehouse) for the chromosome
        self.delivery_ids = [node.id for node in all_nodes if node.id != warehouse_id]

    def _build_distance_matrix(self, nodes):
        """Pre-calculates Euclidean distances between all nodes."""
        matrix = {}
        for i in nodes:
            matrix[i.id] = {}
            for j in nodes:
                matrix[i.id][j.id] = math.hypot(i.x - j.x, i.y - j.y)
        return matrix

    def decode_chromosome(self, chromosome):
        """Translates a 1D sequence of deliveries into specific vehicle routes."""
        decoded_routes = []
        current_gene_index = 0
        
        for vehicle in self.vehicles:
            current_route = [self.warehouse_id]
            current_load = 0
            
            # Fill the vehicle until full or all deliveries are assigned
            while current_load < vehicle.max_capacity and current_gene_index < len(chromosome):
                assigned_node_id = chromosome[current_gene_index]
                current_route.append(assigned_node_id)
                current_load += 1 
                current_gene_index += 1
                
            current_route.append(self.warehouse_id) # Return to warehouse
            
            if len(current_route) > 2: # Only save if it actually made deliveries
                decoded_routes.append({
                    "vehicle_id": vehicle.id,
                    "route": current_route
                })
                
        unassigned_nodes = len(chromosome) - current_gene_index
        return decoded_routes, unassigned_nodes

    def calculate_fitness(self, chromosome):
        """Scores the chromosome. Higher fitness is better (shorter distance)."""
        decoded_routes, unassigned_nodes = self.decode_chromosome(chromosome)
        total_distance = 0.0
        
        for vehicle_data in decoded_routes:
            route = vehicle_data["route"]
            for i in range(len(route) - 1):
                from_node = route[i]
                to_node = route[i+1]
                total_distance += self.distance_matrix[from_node][to_node]
                
        # Heavy penalty if the fleet couldn't carry everything
        if unassigned_nodes > 0:
            total_distance += (unassigned_nodes * 99999) 
            
        return 1.0 / total_distance, total_distance, decoded_routes

    # --- GENETIC ALGORITHM OPERATORS ---

    def _selection_tournament(self, population, tournament_size=3):
        """Picks a random subset and returns the best one (survival of the fittest)."""
        tournament = random.sample(population, tournament_size)
        tournament.sort(key=lambda x: x['fitness'], reverse=True)
        return tournament[0]['chromosome']

    def _crossover_order(self, parent1, parent2):
        """Order Crossover (OX1): Preserves permutation uniqueness."""
        size = len(parent1)
        start, end = sorted(random.sample(range(size), 2))
        
        # Inherit a swath of genes from Parent 1
        child = [None] * size
        child[start:end] = parent1[start:end]
        
        # Fill the rest with Parent 2, skipping duplicates
        p2_filtered = [gene for gene in parent2 if gene not in child]
        
        # Insert remaining genes
        j = 0
        for i in range(size):
            if child[i] is None:
                child[i] = p2_filtered[j]
                j += 1
                
        return child

    def _mutate_inversion(self, chromosome, mutation_rate):
            """
            Inversion mutation: Grabs a random sub-section of the route and reverses it.
            This is highly effective at 'untangling' crossed lines in routing problems.
            """
            mutated = copy.deepcopy(chromosome)
            
            # We check mutation rate against the whole chromosome, not individual genes
            if random.random() < mutation_rate:
                # Pick two random index points
                start, end = sorted(random.sample(range(len(mutated)), 2))
                
                # Reverse the sequence between those two points
                mutated[start:end] = reversed(mutated[start:end])
                
            return mutated

    def run_genetic_algorithm(self, pop_size=100, generations=200, mutation_rate=0.1):
        """The main evolutionary loop."""
        print(f"Starting GA... (Generations: {generations}, Population: {pop_size})")
        
        # 1. Initialize Random Population
        population = []
        for _ in range(pop_size):
            chrom = random.sample(self.delivery_ids, len(self.delivery_ids))
            fitness, cost, routes = self.calculate_fitness(chrom)
            population.append({'chromosome': chrom, 'fitness': fitness, 'cost': cost, 'routes': routes})
            
        best_overall = min(population, key=lambda x: x['cost'])

        # 2. Evolution Loop
        for gen in range(generations):
            new_population = []
            
            # Elitism: Automatically keep the absolute best solution
            new_population.append(best_overall)
            
            while len(new_population) < pop_size:
                # Select parents
                p1 = self._selection_tournament(population)
                p2 = self._selection_tournament(population)
                
                # Crossover
                child_chrom = self._crossover_order(p1, p2)
                
                # Mutate
                child_chrom = self._mutate_inversion(child_chrom, mutation_rate)
                
                # Evaluate new child
                fitness, cost, routes = self.calculate_fitness(child_chrom)
                new_population.append({'chromosome': child_chrom, 'fitness': fitness, 'cost': cost, 'routes': routes})
            
            population = new_population
            
            # Track the best solution
            current_best = min(population, key=lambda x: x['cost'])
            if current_best['cost'] < best_overall['cost']:
                best_overall = current_best
                
            # Print progress every 50 generations
            if gen % 50 == 0 or gen == generations - 1:
                print(f"Generation {gen:3d} | Best Distance: {best_overall['cost']:.2f}")

        print("\n=== EVOLUTION COMPLETE ===")
        return best_overall


# ==========================================
# VISUALIZATION & MAIN EXECUTION
# ==========================================

def plot_routes(nodes_dict, best_solution):
    """Plots the final routes using Matplotlib."""
    if not MATPLOTLIB_AVAILABLE:
        return

    plt.figure(figsize=(8, 8))
    
    # Extract nodes
    warehouse = nodes_dict[0]
    deliveries = [n for n_id, n in nodes_dict.items() if n_id != 0]

    # Plot Nodes
    plt.scatter([n.x for n in deliveries], [n.y for n in deliveries], c='blue', s=50, zorder=3)
    plt.scatter(warehouse.x, warehouse.y, c='red', marker='s', s=100, label='Warehouse', zorder=4)

    # Annotate node IDs
    for n in nodes_dict.values():
        plt.text(n.x + 1.5, n.y + 1.5, str(n.id), fontsize=9, fontweight='bold')

    # Draw Routes
    colors = ['#2ecc71', '#9b59b6', '#e67e22', '#34495e']
    
    for idx, vehicle_data in enumerate(best_solution['routes']):
        route = vehicle_data['route']
        v_id = vehicle_data['vehicle_id']
        color = colors[idx % len(colors)]
        
        # Extract coordinates for the line path
        rx = [nodes_dict[n_id].x for n_id in route]
        ry = [nodes_dict[n_id].y for n_id in route]
        
        plt.plot(rx, ry, color=color, linewidth=2, marker='o', alpha=0.7, label=f'{v_id} Route')

    plt.title(f"Optimized VRP Routes\nTotal Distance: {best_solution['cost']:.2f}")
    plt.grid(True, linestyle='--', alpha=0.5)
    plt.legend()
    plt.show()


if __name__ == "__main__":
    # 1. Setup Random Environment
    warehouse = Node(0, 50, 50) # Keep the warehouse in the center of a 100x100 grid
    
    NUM_DELIVERIES = 25 # <-- Change this number to test your algorithm's scalability!
    delivery_nodes = []
    
    # Generate random coordinates for each delivery node
    for i in range(1, NUM_DELIVERIES + 1):
        rand_x = random.randint(0, 100)
        rand_y = random.randint(0, 100)
        delivery_nodes.append(Node(i, rand_x, rand_y))
        
    all_nodes = [warehouse] + delivery_nodes

    # 2. Setup Fleet Dynamically
    # Each vehicle holds 6 items. We auto-calculate how many vehicles we need.
    VEHICLE_CAPACITY = 6
    num_vehicles_needed = (NUM_DELIVERIES // VEHICLE_CAPACITY) + 1
    
    fleet = []
    for i in range(num_vehicles_needed):
        fleet.append(Vehicle(f"DA_{i+1}", VEHICLE_CAPACITY))

    print(f"--- ENVIRONMENT CREATED ---")
    print(f"Deliveries: {NUM_DELIVERIES} | Vehicles Available: {num_vehicles_needed} (Capacity: {VEHICLE_CAPACITY} each)")
    print("-" * 25)

    # 3. Instantiate and Run the Master Routing Agent
    mra = MasterRoutingAgent(all_nodes, fleet, warehouse_id=0)
    
    # NOTE: Because we have more nodes now, I increased the population size 
    # and generations slightly to give the AI more time to solve the harder puzzle.
    best_solution = mra.run_genetic_algorithm(
        pop_size=300,        # Increased from 150
        generations=1500,    # Increased from 500
        mutation_rate=0.4    # Increased to encourage more untangling
    )

    # 4. Output Results
    print(f"\nFinal Optimized Distance: {best_solution['cost']:.2f}")
    print("Assigned Routes:")
    for v_route in best_solution['routes']:
        print(f" - {v_route['vehicle_id']} path: {v_route['route']}")

    # 5. Visualize
    plot_routes(mra.nodes, best_solution)