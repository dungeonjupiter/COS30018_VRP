from data import create_data
from distance import compute_distance_matrix
from routing import solve_vrp
from visualization import plot_routes


def main():

    depot, warehouses, customers, vehicles = create_data()

    # Combine ALL locations
    locations = [depot] + warehouses + [c.location for c in customers]

    distance_matrix = compute_distance_matrix(locations)

    routes, distances = solve_vrp(
        depot, warehouses, customers, vehicles, locations, distance_matrix
    )

    total_distance = 0

    for i, route in enumerate(routes):
        print(f"\nVehicle {i+1} Route:")

        for loc in route:
            print(f"Location {loc.id}", end=" -> ")

        print(f"\nDistance: {distances[i]}")
        total_distance += distances[i]

    print(f"\nTotal Distance: {total_distance}")

    plot_routes(routes, depot, warehouses, distances)

if __name__ == "__main__":
    main()