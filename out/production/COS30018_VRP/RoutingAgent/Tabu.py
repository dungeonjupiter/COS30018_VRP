"""
Master Routing Agent (MRA) - Tabu Search Implementation
Solves the Capacitated Vehicle Routing Problem (CVRP)
"""
import matplotlib
matplotlib.use('TkAgg') # Force the interactive window backend
import matplotlib.pyplot as plt

import math
import random
import copy

try:
    import matplotlib.pyplot as plt
    MATPLOTLIB_AVAILABLE = True
except ImportError:
    MATPLOTLIB_AVAILABLE = False


# ==========================================
# PHASE 1: DATA STRUCTURES
# ==========================================

class Node:
    def __init__(self, node_id, x, y):
        self.id = node_id
        self.x = x
        self.y = y

class Vehicle:
    def __init__(self, vehicle_id, capacity):
        self.id = vehicle_id
        self.max_capacity = capacity


# ==========================================
# PHASE 2 & 3: TABU SEARCH ALGORITHM
# ==========================================

class TabuSearchMRA:
    def __init__(self, all_nodes, vehicles, warehouse_id=0):
        self.nodes = {node.id: node for node in all_nodes}
        self.vehicles = vehicles
        self.warehouse_id = warehouse_id
        self.distance_matrix = self._build_distance_matrix(all_nodes)
        self.delivery_ids = [node.id for node in all_nodes if node.id != warehouse_id]

    def _build_distance_matrix(self, nodes):
        matrix = {}
        for i in nodes:
            matrix[i.id] = {}
            for j in nodes:
                matrix[i.id][j.id] = math.hypot(i.x - j.x, i.y - j.y)
        return matrix

    def decode_route(self, sequence):
        """Translates the 1D sequence into vehicle routes (Same as GA)"""
        decoded_routes = []
        current_index = 0

        for vehicle in self.vehicles:
            current_route = [self.warehouse_id]
            current_load = 0

            while current_load < vehicle.max_capacity and current_index < len(sequence):
                current_route.append(sequence[current_index])
                current_load += 1
                current_index += 1

            current_route.append(self.warehouse_id)
            if len(current_route) > 2:
                decoded_routes.append({"vehicle_id": vehicle.id, "route": current_route})

        unassigned_nodes = len(sequence) - current_index
        return decoded_routes, unassigned_nodes

    def calculate_cost(self, sequence):
        """Calculates total distance, adding heavy penalties for unassigned nodes."""
        decoded_routes, unassigned_nodes = self.decode_route(sequence)
        total_distance = 0.0

        for vehicle_data in decoded_routes:
            route = vehicle_data["route"]
            for i in range(len(route) - 1):
                from_node = route[i]
                to_node = route[i+1]
                total_distance += self.distance_matrix[from_node][to_node]

        if unassigned_nodes > 0:
            total_distance += (unassigned_nodes * 99999)

        return total_distance, decoded_routes

    def _generate_neighbors(self, sequence):
        """Generates a neighborhood by swapping pairs of nodes."""
        neighbors = []
        # To save compute time on large maps, we only generate a subset of possible swaps
        num_swaps = min(50, len(sequence) * 2)

        for _ in range(num_swaps):
            i, j = random.sample(range(len(sequence)), 2)
            neighbor = copy.deepcopy(sequence)
            neighbor[i], neighbor[j] = neighbor[j], neighbor[i]

            # Record the move (the two nodes that were swapped)
            move = tuple(sorted([sequence[i], sequence[j]]))
            neighbors.append((neighbor, move))

        return neighbors

    def run_tabu_search(self, iterations=500, tabu_tenure=15):
        """The main Tabu Search loop."""
        print(f"Starting Tabu Search... (Iterations: {iterations}, Tabu Tenure: {tabu_tenure})")

        # 1. Initial Solution (Start with a random sequence)
        current_sequence = random.sample(self.delivery_ids, len(self.delivery_ids))
        current_cost, current_routes = self.calculate_cost(current_sequence)

        best_sequence = copy.deepcopy(current_sequence)
        best_cost = current_cost
        best_routes = current_routes

        # Tabu List: Dictionary storing {move: iterations_remaining}
        tabu_list = {}

        # 2. Search Loop
        for iteration in range(iterations):
            neighbors = self._generate_neighbors(current_sequence)

            best_neighbor = None
            best_neighbor_cost = float('inf')
            best_move = None

            for neighbor_seq, move in neighbors:
                cost, routes = self.calculate_cost(neighbor_seq)

                # Aspiration Criterion: If it's the absolute best we've EVER seen,
                # we accept it even if it is on the Tabu list.
                is_tabu = move in tabu_list
                is_best_ever = cost < best_cost

                if not is_tabu or is_best_ever:
                    if cost < best_neighbor_cost:
                        best_neighbor_cost = cost
                        best_neighbor = neighbor_seq
                        best_move = move

            # Move to the best valid neighbor (even if it's worse than the current state!)
            if best_neighbor is not None:
                current_sequence = best_neighbor
                current_cost = best_neighbor_cost

                # Add move to Tabu list
                tabu_list[best_move] = tabu_tenure

                # Update global best
                if current_cost < best_cost:
                    best_cost = current_cost
                    best_sequence = copy.deepcopy(current_sequence)
                    best_routes = self.decode_route(best_sequence)[0]

            # Decrement Tabu tenures
            keys_to_remove = []
            for move in tabu_list:
                tabu_list[move] -= 1
                if tabu_list[move] <= 0:
                    keys_to_remove.append(move)
            for key in keys_to_remove:
                del tabu_list[key]

            if iteration % 50 == 0 or iteration == iterations - 1:
                print(f"Iteration {iteration:3d} | Best Distance: {best_cost:.2f}")

        print("\n=== TABU SEARCH COMPLETE ===")
        return {'cost': best_cost, 'routes': best_routes, 'chromosome': best_sequence}


# ==========================================
# VISUALIZATION & MAIN EXECUTION
# ==========================================
def plot_routes(nodes_dict, best_solution):
    if not MATPLOTLIB_AVAILABLE: return
    plt.figure(figsize=(8, 8))
    warehouse = nodes_dict[0]
    deliveries = [n for n_id, n in nodes_dict.items() if n_id != 0]

    plt.scatter([n.x for n in deliveries], [n.y for n in deliveries], c='blue', s=50, zorder=3)
    plt.scatter(warehouse.x, warehouse.y, c='red', marker='s', s=100, label='Warehouse', zorder=4)

    for n in nodes_dict.values():
        plt.text(n.x + 1.5, n.y + 1.5, str(n.id), fontsize=9, fontweight='bold')

    colors = ['#2ecc71', '#9b59b6', '#e67e22', '#34495e']
    for idx, vehicle_data in enumerate(best_solution['routes']):
        route = vehicle_data['route']
        v_id = vehicle_data['vehicle_id']
        color = colors[idx % len(colors)]
        rx = [nodes_dict[n_id].x for n_id in route]
        ry = [nodes_dict[n_id].y for n_id in route]
        plt.plot(rx, ry, color=color, linewidth=2, marker='o', alpha=0.7, label=f'{v_id} Route')

    plt.title(f"Tabu Search Optimized Routes\nTotal Distance: {best_solution['cost']:.2f}")
    plt.grid(True, linestyle='--', alpha=0.5)
    plt.legend()
    plt.show()

if __name__ == "__main__":
    # Setup Random Environment (25 Nodes)
    warehouse = Node(0, 50, 50)
    NUM_DELIVERIES = 25
    delivery_nodes = [Node(i, random.randint(0, 100), random.randint(0, 100)) for i in range(1, NUM_DELIVERIES + 1)]
    all_nodes = [warehouse] + delivery_nodes

    VEHICLE_CAPACITY = 6
    num_vehicles_needed = (NUM_DELIVERIES // VEHICLE_CAPACITY) + 1
    fleet = [Vehicle(f"DA_{i+1}", VEHICLE_CAPACITY) for i in range(num_vehicles_needed)]

    # Instantiate and Run Tabu Search
    mra = TabuSearchMRA(all_nodes, fleet, warehouse_id=0)
    best_solution = mra.run_tabu_search(iterations=1000, tabu_tenure=20)

    plot_routes(mra.nodes, best_solution)