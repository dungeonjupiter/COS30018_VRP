# Method 2: Baseline Solver Verification — Maps 01–10

> **Solver:** Held-Karp Dynamic Programming + Partition Enumeration — an independent exact solver completely separate from the project's GA and Tabu Search algorithms.  
> **Code base:** Uses the team's `models.py`, `data.py`, `distance.py` from the uploaded OR-Tools project.  
> **Output format:** Identical to what Google OR-Tools `main.py` would print.

---

## Map 01: map01_tiny_compass

**Scenario:** 1 DA · 4 customers · demand=1  
**Customers:** 4 | **Vehicles (DAs):** 1 | **Capacities:** Vehicle1=4

### Solver Output

```
Vehicle 1 Route:
Location 0(50,50) -> Location 1(50,90) -> Location 2(90,50) -> Location 3(50,10) -> Location 4(10,50) -> Location 0(50,50)
Distance: 249.7056

Total Distance: 249.7056
```

### Distance Calculations

**Vehicle 1** (cap=4, load=4/4)
Route: Depot → C1 → C2 → C3 → C4 → Depot

    Depot(50,50) → C1(50,90): √((50−50)² + (90−50)²) = √(0+1600) = √1600 = **40.0000**
    C1(50,90) → C2(90,50): √((90−50)² + (50−90)²) = √(1600+1600) = √3200 = **56.5685**
    C2(90,50) → C3(50,10): √((50−90)² + (10−50)²) = √(1600+1600) = √3200 = **56.5685**
    C3(50,10) → C4(10,50): √((10−50)² + (50−10)²) = √(1600+1600) = √3200 = **56.5685**
    C4(10,50) → Depot(50,50): √((50−10)² + (50−50)²) = √(1600+0) = √1600 = **40.0000**

Subtotal = 40.0000 + 56.5685 + 56.5685 + 56.5685 + 40.0000
**Vehicle 1 distance = 249.7056**

**Grand Total = Vehicle1=249.7056 = 249.7056**

| | Distance |
|---|---|
| Baseline Solver | **249.7056** |
| Ground Truth | **249.71** |
| Verdict | **VERIFIED** |

---

## Map 02: map02_two_clusters

**Scenario:** 2 DAs · 8 customers · 2 clusters  
**Customers:** 8 | **Vehicles (DAs):** 2 | **Capacities:** Vehicle1=4, Vehicle2=4

### Solver Output

```
Vehicle 1 Route:
Location 0(50,50) -> Location 3(5,75) -> Location 1(10,80) -> Location 2(15,85) -> Location 4(20,80) -> Location 0(50,50)
Distance: 115.1178

Vehicle 2 Route:
Location 0(50,50) -> Location 6(90,20) -> Location 5(85,15) -> Location 8(90,10) -> Location 7(80,10) -> Location 0(50,50)
Distance: 124.1421

Total Distance: 239.2599
```

### Distance Calculations

**Vehicle 1** (cap=4, load=4/4)
Route: Depot → C3 → C1 → C2 → C4 → Depot

    Depot(50,50) → C3(5,75): √((5−50)² + (75−50)²) = √(2025+625) = √2650 = **51.4782**
    C3(5,75) → C1(10,80): √((10−5)² + (80−75)²) = √(25+25) = √50 = **7.0711**
    C1(10,80) → C2(15,85): √((15−10)² + (85−80)²) = √(25+25) = √50 = **7.0711**
    C2(15,85) → C4(20,80): √((20−15)² + (80−85)²) = √(25+25) = √50 = **7.0711**
    C4(20,80) → Depot(50,50): √((50−20)² + (50−80)²) = √(900+900) = √1800 = **42.4264**

Subtotal = 51.4782 + 7.0711 + 7.0711 + 7.0711 + 42.4264
**Vehicle 1 distance = 115.1178**

**Vehicle 2** (cap=4, load=4/4)
Route: Depot → C6 → C5 → C8 → C7 → Depot

    Depot(50,50) → C6(90,20): √((90−50)² + (20−50)²) = √(1600+900) = √2500 = **50.0000**
    C6(90,20) → C5(85,15): √((85−90)² + (15−20)²) = √(25+25) = √50 = **7.0711**
    C5(85,15) → C8(90,10): √((90−85)² + (10−15)²) = √(25+25) = √50 = **7.0711**
    C8(90,10) → C7(80,10): √((80−90)² + (10−10)²) = √(100+0) = √100 = **10.0000**
    C7(80,10) → Depot(50,50): √((50−80)² + (50−10)²) = √(900+1600) = √2500 = **50.0000**

Subtotal = 50.0000 + 7.0711 + 7.0711 + 10.0000 + 50.0000
**Vehicle 2 distance = 124.1421**

**Grand Total = Vehicle1=115.1178 + Vehicle2=124.1421 = 239.2599**

| | Distance |
|---|---|
| Baseline Solver | **239.2599** |
| Ground Truth | **239.26** |
| Verdict | **VERIFIED** |

---

## Map 03: map03_three_sectors

**Scenario:** 3 DAs · 9 customers · 3 sectors  
**Customers:** 9 | **Vehicles (DAs):** 3 | **Capacities:** Vehicle1=3, Vehicle2=3, Vehicle3=3

### Solver Output

```
Vehicle 1 Route:
Location 0(50,50) -> Location 2(5,80) -> Location 1(10,90) -> Location 3(20,85) -> Location 0(50,50)
Distance: 122.5417

Vehicle 2 Route:
Location 0(50,50) -> Location 6(80,85) -> Location 4(90,90) -> Location 5(85,80) -> Location 0(50,50)
Distance: 114.5561

Vehicle 3 Route:
Location 0(50,50) -> Location 9(60,8) -> Location 7(50,5) -> Location 8(40,10) -> Location 0(50,50)
Distance: 106.0258

Total Distance: 343.1236
```

### Distance Calculations

**Vehicle 1** (cap=3, load=3/3)
Route: Depot → C2 → C1 → C3 → Depot

    Depot(50,50) → C2(5,80): √((5−50)² + (80−50)²) = √(2025+900) = √2925 = **54.0833**
    C2(5,80) → C1(10,90): √((10−5)² + (90−80)²) = √(25+100) = √125 = **11.1803**
    C1(10,90) → C3(20,85): √((20−10)² + (85−90)²) = √(100+25) = √125 = **11.1803**
    C3(20,85) → Depot(50,50): √((50−20)² + (50−85)²) = √(900+1225) = √2125 = **46.0977**

Subtotal = 54.0833 + 11.1803 + 11.1803 + 46.0977
**Vehicle 1 distance = 122.5417**

**Vehicle 2** (cap=3, load=3/3)
Route: Depot → C6 → C4 → C5 → Depot

    Depot(50,50) → C6(80,85): √((80−50)² + (85−50)²) = √(900+1225) = √2125 = **46.0977**
    C6(80,85) → C4(90,90): √((90−80)² + (90−85)²) = √(100+25) = √125 = **11.1803**
    C4(90,90) → C5(85,80): √((85−90)² + (80−90)²) = √(25+100) = √125 = **11.1803**
    C5(85,80) → Depot(50,50): √((50−85)² + (50−80)²) = √(1225+900) = √2125 = **46.0977**

Subtotal = 46.0977 + 11.1803 + 11.1803 + 46.0977
**Vehicle 2 distance = 114.5561**

**Vehicle 3** (cap=3, load=3/3)
Route: Depot → C9 → C7 → C8 → Depot

    Depot(50,50) → C9(60,8): √((60−50)² + (8−50)²) = √(100+1764) = √1864 = **43.1741**
    C9(60,8) → C7(50,5): √((50−60)² + (5−8)²) = √(100+9) = √109 = **10.4403**
    C7(50,5) → C8(40,10): √((40−50)² + (10−5)²) = √(100+25) = √125 = **11.1803**
    C8(40,10) → Depot(50,50): √((50−40)² + (50−10)²) = √(100+1600) = √1700 = **41.2311**

Subtotal = 43.1741 + 10.4403 + 11.1803 + 41.2311
**Vehicle 3 distance = 106.0258**

**Grand Total = Vehicle1=122.5417 + Vehicle2=114.5561 + Vehicle3=106.0258 = 343.1236**

| | Distance |
|---|---|
| Baseline Solver | **343.1236** |
| Ground Truth | **343.12** |
| Verdict | **VERIFIED** |

---

## Map 04: map04_medium_balanced

**Scenario:** 2 DAs · 12 customers · balanced  
**Customers:** 12 | **Vehicles (DAs):** 2 | **Capacities:** Vehicle1=6, Vehicle2=6

### Solver Output

```
Vehicle 1 Route:
Location 0(50,50) -> Location 6(15,50) -> Location 4(10,30) -> Location 1(10,10) -> Location 3(30,10) -> Location 5(25,20) -> Location 2(20,30) -> Location 0(50,50)
Distance: 154.0317

Vehicle 2 Route:
Location 0(50,50) -> Location 12(75,55) -> Location 9(90,70) -> Location 11(85,75) -> Location 8(80,90) -> Location 10(70,90) -> Location 7(70,70) -> Location 0(50,50)
Distance: 127.8750

Total Distance: 281.9067
```

### Distance Calculations

**Vehicle 1** (cap=6, load=6/6)
Route: Depot → C6 → C4 → C1 → C3 → C5 → C2 → Depot

    Depot(50,50) → C6(15,50): √((15−50)² + (50−50)²) = √(1225+0) = √1225 = **35.0000**
    C6(15,50) → C4(10,30): √((10−15)² + (30−50)²) = √(25+400) = √425 = **20.6155**
    C4(10,30) → C1(10,10): √((10−10)² + (10−30)²) = √(0+400) = √400 = **20.0000**
    C1(10,10) → C3(30,10): √((30−10)² + (10−10)²) = √(400+0) = √400 = **20.0000**
    C3(30,10) → C5(25,20): √((25−30)² + (20−10)²) = √(25+100) = √125 = **11.1803**
    C5(25,20) → C2(20,30): √((20−25)² + (30−20)²) = √(25+100) = √125 = **11.1803**
    C2(20,30) → Depot(50,50): √((50−20)² + (50−30)²) = √(900+400) = √1300 = **36.0555**

Subtotal = 35.0000 + 20.6155 + 20.0000 + 20.0000 + 11.1803 + 11.1803 + 36.0555
**Vehicle 1 distance = 154.0317**

**Vehicle 2** (cap=6, load=6/6)
Route: Depot → C12 → C9 → C11 → C8 → C10 → C7 → Depot

    Depot(50,50) → C12(75,55): √((75−50)² + (55−50)²) = √(625+25) = √650 = **25.4951**
    C12(75,55) → C9(90,70): √((90−75)² + (70−55)²) = √(225+225) = √450 = **21.2132**
    C9(90,70) → C11(85,75): √((85−90)² + (75−70)²) = √(25+25) = √50 = **7.0711**
    C11(85,75) → C8(80,90): √((80−85)² + (90−75)²) = √(25+225) = √250 = **15.8114**
    C8(80,90) → C10(70,90): √((70−80)² + (90−90)²) = √(100+0) = √100 = **10.0000**
    C10(70,90) → C7(70,70): √((70−70)² + (70−90)²) = √(0+400) = √400 = **20.0000**
    C7(70,70) → Depot(50,50): √((50−70)² + (50−70)²) = √(400+400) = √800 = **28.2843**

Subtotal = 25.4951 + 21.2132 + 7.0711 + 15.8114 + 10.0000 + 20.0000 + 28.2843
**Vehicle 2 distance = 127.8750**

**Grand Total = Vehicle1=154.0317 + Vehicle2=127.8750 = 281.9067**

| | Distance |
|---|---|
| Baseline Solver | **281.9067** |
| Ground Truth | **281.91** |
| Verdict | **VERIFIED** |

---

## Map 05: map05_linear_street

**Scenario:** 2 DAs · 6 customers · linear  
**Customers:** 6 | **Vehicles (DAs):** 2 | **Capacities:** Vehicle1=3, Vehicle2=3

### Solver Output

```
Vehicle 1 Route:
Location 0(50,50) -> Location 1(5,50) -> Location 2(25,50) -> Location 3(40,50) -> Location 0(50,50)
Distance: 90.0000

Vehicle 2 Route:
Location 0(50,50) -> Location 4(60,50) -> Location 5(75,50) -> Location 6(95,50) -> Location 0(50,50)
Distance: 90.0000

Total Distance: 180.0000
```

### Distance Calculations

**Vehicle 1** (cap=3, load=3/3)
Route: Depot → C1 → C2 → C3 → Depot

    Depot(50,50) → C1(5,50): √((5−50)² + (50−50)²) = √(2025+0) = √2025 = **45.0000**
    C1(5,50) → C2(25,50): √((25−5)² + (50−50)²) = √(400+0) = √400 = **20.0000**
    C2(25,50) → C3(40,50): √((40−25)² + (50−50)²) = √(225+0) = √225 = **15.0000**
    C3(40,50) → Depot(50,50): √((50−40)² + (50−50)²) = √(100+0) = √100 = **10.0000**

Subtotal = 45.0000 + 20.0000 + 15.0000 + 10.0000
**Vehicle 1 distance = 90.0000**

**Vehicle 2** (cap=3, load=3/3)
Route: Depot → C4 → C5 → C6 → Depot

    Depot(50,50) → C4(60,50): √((60−50)² + (50−50)²) = √(100+0) = √100 = **10.0000**
    C4(60,50) → C5(75,50): √((75−60)² + (50−50)²) = √(225+0) = √225 = **15.0000**
    C5(75,50) → C6(95,50): √((95−75)² + (50−50)²) = √(400+0) = √400 = **20.0000**
    C6(95,50) → Depot(50,50): √((50−95)² + (50−50)²) = √(2025+0) = √2025 = **45.0000**

Subtotal = 10.0000 + 15.0000 + 20.0000 + 45.0000
**Vehicle 2 distance = 90.0000**

**Grand Total = Vehicle1=90.0000 + Vehicle2=90.0000 = 180.0000**

| | Distance |
|---|---|
| Baseline Solver | **180.0000** |
| Ground Truth | **180.0** |
| Verdict | **VERIFIED** |

---

## Map 06: map06_tight_cluster

**Scenario:** WEIRD · 2 DAs · 8 customers · extreme crowding  
**Customers:** 8 | **Vehicles (DAs):** 2 | **Capacities:** Vehicle1=4, Vehicle2=4

### Solver Output

```
Vehicle 1 Route:
Location 0(50,50) -> Location 2(5,7) -> Location 7(4,6) -> Location 1(3,3) -> Location 4(2,8) -> Location 0(50,50)
Distance: 135.6979

Vehicle 2 Route:
Location 0(50,50) -> Location 3(8,2) -> Location 8(7,4) -> Location 5(6,5) -> Location 6(9,9) -> Location 0(50,50)
Distance: 130.4139

Total Distance: 266.1118
```

### Distance Calculations

**Vehicle 1** (cap=4, load=4/4)
Route: Depot → C2 → C7 → C1 → C4 → Depot

    Depot(50,50) → C2(5,7): √((5−50)² + (7−50)²) = √(2025+1849) = √3874 = **62.2415**
    C2(5,7) → C7(4,6): √((4−5)² + (6−7)²) = √(1+1) = √2 = **1.4142**
    C7(4,6) → C1(3,3): √((3−4)² + (3−6)²) = √(1+9) = √10 = **3.1623**
    C1(3,3) → C4(2,8): √((2−3)² + (8−3)²) = √(1+25) = √26 = **5.0990**
    C4(2,8) → Depot(50,50): √((50−2)² + (50−8)²) = √(2304+1764) = √4068 = **63.7809**

Subtotal = 62.2415 + 1.4142 + 3.1623 + 5.0990 + 63.7809
**Vehicle 1 distance = 135.6979**

**Vehicle 2** (cap=4, load=4/4)
Route: Depot → C3 → C8 → C5 → C6 → Depot

    Depot(50,50) → C3(8,2): √((8−50)² + (2−50)²) = √(1764+2304) = √4068 = **63.7809**
    C3(8,2) → C8(7,4): √((7−8)² + (4−2)²) = √(1+4) = √5 = **2.2361**
    C8(7,4) → C5(6,5): √((6−7)² + (5−4)²) = √(1+1) = √2 = **1.4142**
    C5(6,5) → C6(9,9): √((9−6)² + (9−5)²) = √(9+16) = √25 = **5.0000**
    C6(9,9) → Depot(50,50): √((50−9)² + (50−9)²) = √(1681+1681) = √3362 = **57.9828**

Subtotal = 63.7809 + 2.2361 + 1.4142 + 5.0000 + 57.9828
**Vehicle 2 distance = 130.4139**

**Grand Total = Vehicle1=135.6979 + Vehicle2=130.4139 = 266.1118**

| | Distance |
|---|---|
| Baseline Solver | **266.1118** |
| Ground Truth | **266.11** |
| Verdict | **VERIFIED** |

---

## Map 07: map07_outlier

**Scenario:** WEIRD · 2 DAs · 6 customers · extreme outlier  
**Customers:** 6 | **Vehicles (DAs):** 2 | **Capacities:** Vehicle1=3, Vehicle2=3

### Solver Output

```
Vehicle 1 Route:
Location 0(50,50) -> Location 1(45,55) -> Location 6(99,99) -> Location 4(60,60) -> Location 0(50,50)
Distance: 146.0238

Vehicle 2 Route:
Location 0(50,50) -> Location 2(55,45) -> Location 5(50,30) -> Location 3(40,40) -> Location 0(50,50)
Distance: 51.1667

Total Distance: 197.1906
```

### Distance Calculations

**Vehicle 1** (cap=3, load=3/3)
Route: Depot → C1 → C6 → C4 → Depot

    Depot(50,50) → C1(45,55): √((45−50)² + (55−50)²) = √(25+25) = √50 = **7.0711**
    C1(45,55) → C6(99,99): √((99−45)² + (99−55)²) = √(2916+1936) = √4852 = **69.6563**
    C6(99,99) → C4(60,60): √((60−99)² + (60−99)²) = √(1521+1521) = √3042 = **55.1543**
    C4(60,60) → Depot(50,50): √((50−60)² + (50−60)²) = √(100+100) = √200 = **14.1421**

Subtotal = 7.0711 + 69.6563 + 55.1543 + 14.1421
**Vehicle 1 distance = 146.0238**

**Vehicle 2** (cap=3, load=3/3)
Route: Depot → C2 → C5 → C3 → Depot

    Depot(50,50) → C2(55,45): √((55−50)² + (45−50)²) = √(25+25) = √50 = **7.0711**
    C2(55,45) → C5(50,30): √((50−55)² + (30−45)²) = √(25+225) = √250 = **15.8114**
    C5(50,30) → C3(40,40): √((40−50)² + (40−30)²) = √(100+100) = √200 = **14.1421**
    C3(40,40) → Depot(50,50): √((50−40)² + (50−40)²) = √(100+100) = √200 = **14.1421**

Subtotal = 7.0711 + 15.8114 + 14.1421 + 14.1421
**Vehicle 2 distance = 51.1667**

**Grand Total = Vehicle1=146.0238 + Vehicle2=51.1667 = 197.1906**

| | Distance |
|---|---|
| Baseline Solver | **197.1906** |
| Ground Truth | **197.19** |
| Verdict | **VERIFIED** |

---

## Map 08: map08_one_sided

**Scenario:** WEIRD · 3 DAs · 9 customers · left side only  
**Customers:** 9 | **Vehicles (DAs):** 3 | **Capacities:** Vehicle1=3, Vehicle2=3, Vehicle3=3

### Solver Output

```
Vehicle 1 Route:
Location 0(50,50) -> Location 2(10,30) -> Location 7(10,20) -> Location 1(5,10) -> Location 0(50,50)
Distance: 126.1097

Vehicle 2 Route:
Location 0(50,50) -> Location 8(20,40) -> Location 3(5,50) -> Location 6(20,50) -> Location 0(50,50)
Distance: 94.6505

Vehicle 3 Route:
Location 0(50,50) -> Location 5(5,90) -> Location 4(15,70) -> Location 9(15,60) -> Location 0(50,50)
Distance: 128.9692

Total Distance: 349.7294
```

### Distance Calculations

**Vehicle 1** (cap=3, load=3/3)
Route: Depot → C2 → C7 → C1 → Depot

    Depot(50,50) → C2(10,30): √((10−50)² + (30−50)²) = √(1600+400) = √2000 = **44.7214**
    C2(10,30) → C7(10,20): √((10−10)² + (20−30)²) = √(0+100) = √100 = **10.0000**
    C7(10,20) → C1(5,10): √((5−10)² + (10−20)²) = √(25+100) = √125 = **11.1803**
    C1(5,10) → Depot(50,50): √((50−5)² + (50−10)²) = √(2025+1600) = √3625 = **60.2080**

Subtotal = 44.7214 + 10.0000 + 11.1803 + 60.2080
**Vehicle 1 distance = 126.1097**

**Vehicle 2** (cap=3, load=3/3)
Route: Depot → C8 → C3 → C6 → Depot

    Depot(50,50) → C8(20,40): √((20−50)² + (40−50)²) = √(900+100) = √1000 = **31.6228**
    C8(20,40) → C3(5,50): √((5−20)² + (50−40)²) = √(225+100) = √325 = **18.0278**
    C3(5,50) → C6(20,50): √((20−5)² + (50−50)²) = √(225+0) = √225 = **15.0000**
    C6(20,50) → Depot(50,50): √((50−20)² + (50−50)²) = √(900+0) = √900 = **30.0000**

Subtotal = 31.6228 + 18.0278 + 15.0000 + 30.0000
**Vehicle 2 distance = 94.6505**

**Vehicle 3** (cap=3, load=3/3)
Route: Depot → C5 → C4 → C9 → Depot

    Depot(50,50) → C5(5,90): √((5−50)² + (90−50)²) = √(2025+1600) = √3625 = **60.2080**
    C5(5,90) → C4(15,70): √((15−5)² + (70−90)²) = √(100+400) = √500 = **22.3607**
    C4(15,70) → C9(15,60): √((15−15)² + (60−70)²) = √(0+100) = √100 = **10.0000**
    C9(15,60) → Depot(50,50): √((50−15)² + (50−60)²) = √(1225+100) = √1325 = **36.4005**

Subtotal = 60.2080 + 22.3607 + 10.0000 + 36.4005
**Vehicle 3 distance = 128.9692**

**Grand Total = Vehicle1=126.1097 + Vehicle2=94.6505 + Vehicle3=128.9692 = 349.7294**

| | Distance |
|---|---|
| Baseline Solver | **349.7294** |
| Ground Truth | **349.73** |
| Verdict | **VERIFIED** |

---

## Map 09: map09_star_pattern

**Scenario:** WEIRD · 2 DAs · 8 customers · equidistant star  
**Customers:** 8 | **Vehicles (DAs):** 2 | **Capacities:** Vehicle1=4, Vehicle2=4

### Solver Output

```
Vehicle 1 Route:
Location 0(50,50) -> Location 4(75,25) -> Location 3(85,50) -> Location 2(75,75) -> Location 1(50,85) -> Location 0(50,50)
Distance: 151.1328

Vehicle 2 Route:
Location 0(50,50) -> Location 8(25,75) -> Location 7(15,50) -> Location 6(25,25) -> Location 5(50,15) -> Location 0(50,50)
Distance: 151.1328

Total Distance: 302.2656
```

### Distance Calculations

**Vehicle 1** (cap=4, load=4/4)
Route: Depot → C4 → C3 → C2 → C1 → Depot

    Depot(50,50) → C4(75,25): √((75−50)² + (25−50)²) = √(625+625) = √1250 = **35.3553**
    C4(75,25) → C3(85,50): √((85−75)² + (50−25)²) = √(100+625) = √725 = **26.9258**
    C3(85,50) → C2(75,75): √((75−85)² + (75−50)²) = √(100+625) = √725 = **26.9258**
    C2(75,75) → C1(50,85): √((50−75)² + (85−75)²) = √(625+100) = √725 = **26.9258**
    C1(50,85) → Depot(50,50): √((50−50)² + (50−85)²) = √(0+1225) = √1225 = **35.0000**

Subtotal = 35.3553 + 26.9258 + 26.9258 + 26.9258 + 35.0000
**Vehicle 1 distance = 151.1328**

**Vehicle 2** (cap=4, load=4/4)
Route: Depot → C8 → C7 → C6 → C5 → Depot

    Depot(50,50) → C8(25,75): √((25−50)² + (75−50)²) = √(625+625) = √1250 = **35.3553**
    C8(25,75) → C7(15,50): √((15−25)² + (50−75)²) = √(100+625) = √725 = **26.9258**
    C7(15,50) → C6(25,25): √((25−15)² + (25−50)²) = √(100+625) = √725 = **26.9258**
    C6(25,25) → C5(50,15): √((50−25)² + (15−25)²) = √(625+100) = √725 = **26.9258**
    C5(50,15) → Depot(50,50): √((50−50)² + (50−15)²) = √(0+1225) = √1225 = **35.0000**

Subtotal = 35.3553 + 26.9258 + 26.9258 + 26.9258 + 35.0000
**Vehicle 2 distance = 151.1328**

**Grand Total = Vehicle1=151.1328 + Vehicle2=151.1328 = 302.2656**

| | Distance |
|---|---|
| Baseline Solver | **302.2656** |
| Ground Truth | **302.27** |
| Verdict | **VERIFIED** |

---

## Map 10: map10_unequal_demand

**Scenario:** WEIRD · 3 DAs · 10 customers · demand 1-3  
**Customers:** 10 | **Vehicles (DAs):** 3 | **Capacities:** Vehicle1=7, Vehicle2=7, Vehicle3=7

### Solver Output

```
Vehicle 1 Route:
Location 0(50,50) -> Location 9(40,60) -> Location 7(30,70) -> Location 4(70,30) -> Location 0(50,50)
Distance: 113.1371

Vehicle 2 Route:
Location 0(50,50) -> Location 6(60,80) -> Location 10(90,90) -> Location 5(80,60) -> Location 0(50,50)
Distance: 126.4911

Vehicle 3 Route:
Location 0(50,50) -> Location 8(50,20) -> Location 2(30,20) -> Location 1(10,10) -> Location 3(20,40) -> Location 0(50,50)
Distance: 135.6062

Total Distance: 375.2344
```

### Distance Calculations

**Vehicle 1** (cap=7, load=6/7)
Route: Depot → C9 → C7 → C4 → Depot

    Depot(50,50) → C9(40,60): √((40−50)² + (60−50)²) = √(100+100) = √200 = **14.1421**
    C9(40,60) → C7(30,70): √((30−40)² + (70−60)²) = √(100+100) = √200 = **14.1421**
    C7(30,70) → C4(70,30): √((70−30)² + (30−70)²) = √(1600+1600) = √3200 = **56.5685**
    C4(70,30) → Depot(50,50): √((50−70)² + (50−30)²) = √(400+400) = √800 = **28.2843**

Subtotal = 14.1421 + 14.1421 + 56.5685 + 28.2843
**Vehicle 1 distance = 113.1371**

**Vehicle 2** (cap=7, load=7/7)
Route: Depot → C6 → C10 → C5 → Depot

    Depot(50,50) → C6(60,80): √((60−50)² + (80−50)²) = √(100+900) = √1000 = **31.6228**
    C6(60,80) → C10(90,90): √((90−60)² + (90−80)²) = √(900+100) = √1000 = **31.6228**
    C10(90,90) → C5(80,60): √((80−90)² + (60−90)²) = √(100+900) = √1000 = **31.6228**
    C5(80,60) → Depot(50,50): √((50−80)² + (50−60)²) = √(900+100) = √1000 = **31.6228**

Subtotal = 31.6228 + 31.6228 + 31.6228 + 31.6228
**Vehicle 2 distance = 126.4911**

**Vehicle 3** (cap=7, load=7/7)
Route: Depot → C8 → C2 → C1 → C3 → Depot

    Depot(50,50) → C8(50,20): √((50−50)² + (20−50)²) = √(0+900) = √900 = **30.0000**
    C8(50,20) → C2(30,20): √((30−50)² + (20−20)²) = √(400+0) = √400 = **20.0000**
    C2(30,20) → C1(10,10): √((10−30)² + (10−20)²) = √(400+100) = √500 = **22.3607**
    C1(10,10) → C3(20,40): √((20−10)² + (40−10)²) = √(100+900) = √1000 = **31.6228**
    C3(20,40) → Depot(50,50): √((50−20)² + (50−40)²) = √(900+100) = √1000 = **31.6228**

Subtotal = 30.0000 + 20.0000 + 22.3607 + 31.6228 + 31.6228
**Vehicle 3 distance = 135.6062**

**Grand Total = Vehicle1=113.1371 + Vehicle2=126.4911 + Vehicle3=135.6062 = 375.2344**

| | Distance |
|---|---|
| Baseline Solver | **375.2344** |
| Ground Truth | **375.23** |
| Verdict | **VERIFIED** |

---

## Summary

| Map | Name | Ground Truth | Solver Result | Verdict |
|---|---|---|---|---|
| 01 | map01_tiny_compass | 249.71 | 249.7056 | VERIFIED |
| 02 | map02_two_clusters | 239.26 | 239.2599 | VERIFIED |
| 03 | map03_three_sectors | 343.12 | 343.1236 | VERIFIED |
| 04 | map04_medium_balanced | 281.91 | 281.9067 | VERIFIED |
| 05 | map05_linear_street | 180.0 | 180.0000 | VERIFIED |
| 06 | map06_tight_cluster | 266.11 | 266.1118 | VERIFIED |
| 07 | map07_outlier | 197.19 | 197.1906 | VERIFIED |
| 08 | map08_one_sided | 349.73 | 349.7294 | VERIFIED |
| 09 | map09_star_pattern | 302.27 | 302.2656 | VERIFIED |
| 10 | map10_unequal_demand | 375.23 | 375.2344 | VERIFIED |

**All 10 ground truth values independently verified by the Held-Karp exact solver.**
