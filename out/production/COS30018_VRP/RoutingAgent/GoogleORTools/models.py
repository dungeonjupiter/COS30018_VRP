class Location:
    def __init__(self, id, x, y):
        self.id = id
        self.x = x
        self.y = y


class Customer:
    def __init__(self, id, location, demand=0):
        self.id = id
        self.location = location
        self.demand = demand


class Vehicle:
    def __init__(self, id, capacity):
        self.id = id
        self.capacity = capacity
        self.route = []