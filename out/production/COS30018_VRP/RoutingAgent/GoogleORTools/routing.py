from ortools.constraint_solver import pywrapcp, routing_enums_pb2


def solve_vrp(depot, warehouses, customers, vehicles, locations, distance_matrix):

    num_vehicles = len(vehicles)

    # Start and end all vehicles at depot (index 0)
    starts = [0] * num_vehicles
    ends = [0] * num_vehicles

    manager = pywrapcp.RoutingIndexManager(
        len(locations),
        num_vehicles,
        starts,
        ends
    )

    routing = pywrapcp.RoutingModel(manager)

    # Distance callback
    def distance_callback(from_index, to_index):
        from_node = manager.IndexToNode(from_index)
        to_node = manager.IndexToNode(to_index)
        return distance_matrix[from_node][to_node]

    transit_callback_index = routing.RegisterTransitCallback(distance_callback)
    routing.SetArcCostEvaluatorOfAllVehicles(transit_callback_index)

    # Demand (customers only)
    demands = [0] * len(locations)

    # Customers start after depot + warehouses
    offset = 1 + len(warehouses)

    for i, c in enumerate(customers):
        demands[offset + i] = c.demand

    capacities = [v.capacity for v in vehicles]

    def demand_callback(from_index):
        node = manager.IndexToNode(from_index)
        return demands[node]

    demand_callback_index = routing.RegisterUnaryTransitCallback(demand_callback)

    routing.AddDimensionWithVehicleCapacity(
        demand_callback_index,
        0,
        capacities,
        True,
        "Capacity"
    )

    # Solve
    search_parameters = pywrapcp.DefaultRoutingSearchParameters()
    search_parameters.first_solution_strategy = (
        routing_enums_pb2.FirstSolutionStrategy.PATH_CHEAPEST_ARC
    )

    solution = routing.SolveWithParameters(search_parameters)

    routes = []
    distances = []

    if solution:
        for vehicle_id in range(num_vehicles):
            index = routing.Start(vehicle_id)
            route = []
            route_distance = 0

            while not routing.IsEnd(index):
                node = manager.IndexToNode(index)
                route.append(locations[node])

                prev_index = index
                index = solution.Value(routing.NextVar(index))

                route_distance += routing.GetArcCostForVehicle(
                    prev_index, index, vehicle_id
                )

            route.append(locations[0])  # back to depot

            routes.append(route)
            distances.append(route_distance)

    return routes, distances