import math


def compute_distance_matrix(locations):
    matrix = []

    for from_loc in locations:
        row = []
        for to_loc in locations:
            dist = int(math.hypot(from_loc.x - to_loc.x,
                                  from_loc.y - to_loc.y))
            row.append(dist)
        matrix.append(row)

    return matrix