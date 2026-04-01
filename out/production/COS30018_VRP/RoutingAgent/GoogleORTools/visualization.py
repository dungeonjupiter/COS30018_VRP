import matplotlib.pyplot as plt


def plot_routes(routes, depot, warehouses, distances):

    plt.figure(figsize=(8, 8))

    colors = ['blue', 'green', 'red', 'purple', 'orange']

    # -----------------------------
    # Plot Depot
    # -----------------------------
    plt.scatter(depot.x, depot.y,
                c='black', s=150, marker='s', label='Central Depot')

    plt.text(depot.x, depot.y, f"D({depot.id})", fontsize=10, ha='right')

    # -----------------------------
    # Plot Warehouses
    # -----------------------------
    for i, wh in enumerate(warehouses):
        plt.scatter(wh.x, wh.y,
                    c='orange', s=100, marker='^')

        plt.text(wh.x, wh.y, f"W({wh.id})", fontsize=9)

    # Add one legend entry only
    plt.scatter([], [], c='orange', marker='^', label='Warehouse')

    # -----------------------------
    # Plot Routes
    # -----------------------------
    for i, route in enumerate(routes):

        x = [loc.x for loc in route]
        y = [loc.y for loc in route]

        color = colors[i % len(colors)]

        plt.plot(x, y, marker='o',
                 color=color,
                 label=f'Vehicle {i+1} (Dist={distances[i]})')

        # Draw arrows
        for j in range(len(route) - 1):
            dx = route[j+1].x - route[j].x
            dy = route[j+1].y - route[j].y

            plt.arrow(route[j].x, route[j].y,
                      dx * 0.8, dy * 0.8,
                      head_width=0.3,
                      length_includes_head=True,
                      color=color,
                      alpha=0.6)

        # Label customers
        for loc in route:
            if loc.id != depot.id:
                plt.text(loc.x, loc.y,
                         f"{loc.id}",
                         fontsize=9,
                         ha='center',
                         va='bottom')

    # -----------------------------
    # Styling
    # -----------------------------
    plt.title("Vehicle Routing Problem (Advanced Visualization)")
    plt.xlabel("X Coordinate")
    plt.ylabel("Y Coordinate")

    total_distance = sum(distances)
    plt.title(f"VRP Solution (Total Distance = {total_distance})")

    plt.legend()
    plt.grid(True)

    plt.tight_layout()
    plt.show()