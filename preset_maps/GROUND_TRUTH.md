# VRP Preset Maps — Ground Truth Reference

> **Warehouse:** (50, 50)  |  **Map grid:** 100 × 100  |  **Distance:** Euclidean

Each `.txt` file contains one customer per line in `x,y,demand` format.  
Set the JADE MRA GUI fields as shown under **Config** for each map.

---

## map01_tiny_compass

**Scenario:** 1 DA · 4 customers · demand=1  
**Description:** 4 customers at compass points ~40 units from the warehouse. Single DA handles all. Ideal route is a simple clockwise (or CCW) loop. Great sanity-check baseline.

### Config (JADE MRA GUI)
| Field | Value |
|---|---|
| Num Customers | `4` |
| Num DAs | `1` |
| DA Capacities | `DA1=4` |
| File Path | `preset_maps/map01_tiny_compass.txt` |
| Total System Demand | `4` |

### Customer Nodes
| # | X | Y | Demand |
|---|---|---|---|
| C1 | 50 | 90 | 1 |
| C2 | 90 | 50 | 1 |
| C3 | 50 | 10 | 1 |
| C4 | 10 | 50 | 1 |

### Ground Truth Best Path — Total: `249.71`

- **DA1** (cap=4, load=4/4): Depot → C1 → C2 → C3 → C4 → Depot `dist=249.71`

---

## map02_two_clusters

**Scenario:** 2 DAs · 8 customers · 2 clear clusters  
**Description:** 8 customers split into a top-left cluster and a bottom-right cluster. Ideal: one DA per cluster. Tests whether the algorithm discovers the natural geographic partition.

### Config (JADE MRA GUI)
| Field | Value |
|---|---|
| Num Customers | `8` |
| Num DAs | `2` |
| DA Capacities | `DA1=4, DA2=4` |
| File Path | `preset_maps/map02_two_clusters.txt` |
| Total System Demand | `8` |

### Customer Nodes
| # | X | Y | Demand |
|---|---|---|---|
| C1 | 10 | 80 | 1 |
| C2 | 15 | 85 | 1 |
| C3 | 5 | 75 | 1 |
| C4 | 20 | 80 | 1 |
| C5 | 85 | 15 | 1 |
| C6 | 90 | 20 | 1 |
| C7 | 80 | 10 | 1 |
| C8 | 90 | 10 | 1 |

### Ground Truth Best Path — Total: `239.26`

- **DA1** (cap=4, load=4/4): Depot → C3 → C1 → C2 → C4 → Depot `dist=115.12`
- **DA2** (cap=4, load=4/4): Depot → C6 → C5 → C8 → C7 → Depot `dist=124.14`

---

## map03_three_sectors

**Scenario:** 3 DAs · 9 customers · 3 geographic sectors  
**Description:** 9 customers in three clear zones: top-left, top-right, and bottom-centre. Perfect assignment is one DA per zone.

### Config (JADE MRA GUI)
| Field | Value |
|---|---|
| Num Customers | `9` |
| Num DAs | `3` |
| DA Capacities | `DA1=3, DA2=3, DA3=3` |
| File Path | `preset_maps/map03_three_sectors.txt` |
| Total System Demand | `9` |

### Customer Nodes
| # | X | Y | Demand |
|---|---|---|---|
| C1 | 10 | 90 | 1 |
| C2 | 5 | 80 | 1 |
| C3 | 20 | 85 | 1 |
| C4 | 90 | 90 | 1 |
| C5 | 85 | 80 | 1 |
| C6 | 80 | 85 | 1 |
| C7 | 50 | 5 | 1 |
| C8 | 40 | 10 | 1 |
| C9 | 60 | 8 | 1 |

### Ground Truth Best Path — Total: `343.12`

- **DA1** (cap=3, load=3/3): Depot → C8 → C7 → C9 → Depot `dist=106.03`
- **DA2** (cap=3, load=3/3): Depot → C3 → C1 → C2 → Depot `dist=122.54`
- **DA3** (cap=3, load=3/3): Depot → C5 → C4 → C6 → Depot `dist=114.56`

---

## map04_medium_balanced

**Scenario:** 2 DAs · 12 customers · balanced spread  
**Description:** 12 customers split between a bottom-left cluster and a top-right cluster. Good medium-scale test; both DAs travel similar distances.

### Config (JADE MRA GUI)
| Field | Value |
|---|---|
| Num Customers | `12` |
| Num DAs | `2` |
| DA Capacities | `DA1=6, DA2=6` |
| File Path | `preset_maps/map04_medium_balanced.txt` |
| Total System Demand | `12` |

### Customer Nodes
| # | X | Y | Demand |
|---|---|---|---|
| C1 | 10 | 10 | 1 |
| C2 | 20 | 30 | 1 |
| C3 | 30 | 10 | 1 |
| C4 | 10 | 30 | 1 |
| C5 | 25 | 20 | 1 |
| C6 | 15 | 50 | 1 |
| C7 | 70 | 70 | 1 |
| C8 | 80 | 90 | 1 |
| C9 | 90 | 70 | 1 |
| C10 | 70 | 90 | 1 |
| C11 | 85 | 75 | 1 |
| C12 | 75 | 55 | 1 |

### Ground Truth Best Path — Total: `281.91`

- **DA1** (cap=6, load=6/6): Depot → C7 → C10 → C8 → C11 → C9 → C12 → Depot `dist=127.88`
- **DA2** (cap=6, load=6/6): Depot → C2 → C5 → C3 → C1 → C4 → C6 → Depot `dist=154.03`

---

## map05_linear_street

**Scenario:** 2 DAs · 6 customers · perfect straight line  
**Description:** All 6 customers lie on y=50 (the same horizontal as the warehouse). Optimal split is the 3 left-of-warehouse vs 3 right-of-warehouse. Should give a perfectly symmetric result.

### Config (JADE MRA GUI)
| Field | Value |
|---|---|
| Num Customers | `6` |
| Num DAs | `2` |
| DA Capacities | `DA1=3, DA2=3` |
| File Path | `preset_maps/map05_linear_street.txt` |
| Total System Demand | `6` |

### Customer Nodes
| # | X | Y | Demand |
|---|---|---|---|
| C1 | 5 | 50 | 1 |
| C2 | 25 | 50 | 1 |
| C3 | 40 | 50 | 1 |
| C4 | 60 | 50 | 1 |
| C5 | 75 | 50 | 1 |
| C6 | 95 | 50 | 1 |

### Ground Truth Best Path — Total: `180.00`

- **DA1** (cap=3, load=3/3): Depot → C1 → C2 → C3 → Depot `dist=90.00`
- **DA2** (cap=3, load=3/3): Depot → C4 → C5 → C6 → Depot `dist=90.00`

---

## map06_tight_cluster

**Scenario:** WEIRD · 2 DAs · 8 customers · extreme crowding in 10×10 corner  
**Description:** All customers crammed into a tiny 10×10 patch near (5,5). Both DAs must travel far to reach the cluster, then make cheap local hops. Highlights that inter-cluster distance dominates.

### Config (JADE MRA GUI)
| Field | Value |
|---|---|
| Num Customers | `8` |
| Num DAs | `2` |
| DA Capacities | `DA1=4, DA2=4` |
| File Path | `preset_maps/map06_tight_cluster.txt` |
| Total System Demand | `8` |

### Customer Nodes
| # | X | Y | Demand |
|---|---|---|---|
| C1 | 3 | 3 | 1 |
| C2 | 5 | 7 | 1 |
| C3 | 8 | 2 | 1 |
| C4 | 2 | 8 | 1 |
| C5 | 6 | 5 | 1 |
| C6 | 9 | 9 | 1 |
| C7 | 4 | 6 | 1 |
| C8 | 7 | 4 | 1 |

### Ground Truth Best Path — Total: `266.11`

- **DA1** (cap=4, load=4/4): Depot → C2 → C7 → C1 → C4 → Depot `dist=135.70`
- **DA2** (cap=4, load=4/4): Depot → C3 → C8 → C5 → C6 → Depot `dist=130.41`

---

## map07_outlier

**Scenario:** WEIRD · 2 DAs · 6 customers · 1 extreme outlier at (99,99)  
**Description:** Five customers cluster near the warehouse; one sits at the far corner (99,99). One DA must absorb the costly detour. Checks whether the algorithm isolates the outlier efficiently.

### Config (JADE MRA GUI)
| Field | Value |
|---|---|
| Num Customers | `6` |
| Num DAs | `2` |
| DA Capacities | `DA1=3, DA2=3` |
| File Path | `preset_maps/map07_outlier.txt` |
| Total System Demand | `6` |

### Customer Nodes
| # | X | Y | Demand |
|---|---|---|---|
| C1 | 45 | 55 | 1 |
| C2 | 55 | 45 | 1 |
| C3 | 40 | 40 | 1 |
| C4 | 60 | 60 | 1 |
| C5 | 50 | 30 | 1 |
| C6 | 99 | 99 | 1 |

### Ground Truth Best Path — Total: `197.19`

- **DA1** (cap=3, load=3/3): Depot → C1 → C6 → C4 → Depot `dist=146.02`
- **DA2** (cap=3, load=3/3): Depot → C2 → C5 → C3 → Depot `dist=51.17`

---

## map08_one_sided

**Scenario:** WEIRD · 3 DAs · 9 customers · all on LEFT half (x≤20)  
**Description:** Every customer is on the extreme left side; the warehouse sits in the centre. All three DAs travel left — the right half of the map is empty. Tests handling of skewed geographic distribution.

### Config (JADE MRA GUI)
| Field | Value |
|---|---|
| Num Customers | `9` |
| Num DAs | `3` |
| DA Capacities | `DA1=3, DA2=3, DA3=3` |
| File Path | `preset_maps/map08_one_sided.txt` |
| Total System Demand | `9` |

### Customer Nodes
| # | X | Y | Demand |
|---|---|---|---|
| C1 | 5 | 10 | 1 |
| C2 | 10 | 30 | 1 |
| C3 | 5 | 50 | 1 |
| C4 | 15 | 70 | 1 |
| C5 | 5 | 90 | 1 |
| C6 | 20 | 50 | 1 |
| C7 | 10 | 20 | 1 |
| C8 | 20 | 40 | 1 |
| C9 | 15 | 60 | 1 |

### Ground Truth Best Path — Total: `349.73`

- **DA1** (cap=3, load=3/3): Depot → C1 → C7 → C2 → Depot `dist=126.11`
- **DA2** (cap=3, load=3/3): Depot → C6 → C3 → C8 → Depot `dist=94.65`
- **DA3** (cap=3, load=3/3): Depot → C5 → C4 → C9 → Depot `dist=128.97`

---

## map09_star_pattern

**Scenario:** WEIRD · 2 DAs · 8 customers · perfect octagon equidistant from warehouse  
**Description:** 8 customers arranged in a regular octagon ~35 units from the warehouse. All equally far — the algorithm can't use distance to warehouse as a tiebreaker. Best solution groups adjacent arc segments, not criss-crossing halves.

### Config (JADE MRA GUI)
| Field | Value |
|---|---|
| Num Customers | `8` |
| Num DAs | `2` |
| DA Capacities | `DA1=4, DA2=4` |
| File Path | `preset_maps/map09_star_pattern.txt` |
| Total System Demand | `8` |

### Customer Nodes
| # | X | Y | Demand |
|---|---|---|---|
| C1 | 50 | 85 | 1 |
| C2 | 75 | 75 | 1 |
| C3 | 85 | 50 | 1 |
| C4 | 75 | 25 | 1 |
| C5 | 50 | 15 | 1 |
| C6 | 25 | 25 | 1 |
| C7 | 15 | 50 | 1 |
| C8 | 25 | 75 | 1 |

### Ground Truth Best Path — Total: `302.27`

- **DA1** (cap=4, load=4/4): Depot → C1 → C2 → C3 → C4 → Depot `dist=151.13`
- **DA2** (cap=4, load=4/4): Depot → C5 → C6 → C7 → C8 → Depot `dist=151.13`

---

## map10_unequal_demand

**Scenario:** WEIRD · 3 DAs · 10 customers · demand 1–3 (total=20)  
**Description:** Customers carry demands of 1, 2, or 3; each DA capacity is 7. Total demand (20) nearly fills all three vehicles. Pure customer-count splits will fail — the algorithm must bin-pack demands correctly.

### Config (JADE MRA GUI)
| Field | Value |
|---|---|
| Num Customers | `10` |
| Num DAs | `3` |
| DA Capacities | `DA1=7, DA2=7, DA3=7` |
| File Path | `preset_maps/map10_unequal_demand.txt` |
| Total System Demand | `20` |

### Customer Nodes
| # | X | Y | Demand |
|---|---|---|---|
| C1 | 10 | 10 | 3 |
| C2 | 30 | 20 | 2 |
| C3 | 20 | 40 | 1 |
| C4 | 70 | 30 | 2 |
| C5 | 80 | 60 | 3 |
| C6 | 60 | 80 | 1 |
| C7 | 30 | 70 | 2 |
| C8 | 50 | 20 | 1 |
| C9 | 40 | 60 | 2 |
| C10 | 90 | 90 | 3 |

### Ground Truth Best Path — Total: `375.23`

- **DA1** (cap=7, load=7/7): Depot → C3 → C1 → C2 → C8 → Depot `dist=135.61`
- **DA2** (cap=7, load=6/7): Depot → C4 → C7 → C9 → Depot `dist=113.14`
- **DA3** (cap=7, load=7/7): Depot → C5 → C10 → C6 → Depot `dist=126.49`

---

