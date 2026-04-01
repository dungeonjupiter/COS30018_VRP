from models import Location, Customer, Vehicle


def create_data():

    # Central depot (middle)
    depot = Location(0, 5, 5)

    # Warehouses (extra nodes, optional usage)
    warehouses = [
        Location(6, 0, 0),
        Location(7, 10, 0),
        Location(8, 0, 10),
        Location(9, 10, 10)
    ]

    customers = [
        Customer(1, Location(1, 2, 3), 10),
        Customer(2, Location(2, 6, 2), 15),
        Customer(3, Location(3, 8, 6), 20),
        Customer(4, Location(4, 1, 8), 10),
        Customer(5, Location(5, 9, 9), 25),
    ]

    vehicles = [
        Vehicle(1, 40),
        Vehicle(2, 40),
        Vehicle(3, 40)
    ]

    return depot, warehouses, customers, vehicles