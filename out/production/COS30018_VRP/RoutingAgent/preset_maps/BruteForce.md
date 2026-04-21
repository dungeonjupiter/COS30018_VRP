# Method 1: Brute Force Verification — Maps 01–10

> **Proof standard:** Every mathematically possible assignment of customers to DAs was evaluated. The lowest distance found is the **proven global optimum** — no better solution can exist.

> **Maps 01, 02, 05, 06, 07:** Full brute force — all partitions × all route orderings enumerated.  
> **Maps 03, 04, 08, 09, 10:** Partition brute force + Held-Karp exact route ordering (equivalent, still proven optimal).  
> **Depot:** (50,50) | **Formula:** √((x₂−x₁)²+(y₂−y₁)²)

---

## Map 01: map01_tiny_compass

**Scenario:** 1 DA · 4 customers · demand=1  
**Customers:** 4 | **DAs:** 1 | **Capacities:** DA1=4

### Search Space

| Metric | Value |
|---|---|
| Valid partitions checked | **1** |
| Route orderings checked | **24** |

### Optimal Solution Found

**DA1** (cap=4, load=4/4)
Route: Depot → C1 → C2 → C3 → C4 → Depot

    Depot(50,50) → C1(50,90): √((50−50)² + (90−50)²) = √(0+1600) = √1600 = **40.0000**
    C1(50,90) → C2(90,50): √((90−50)² + (50−90)²) = √(1600+1600) = √3200 = **56.5685**
    C2(90,50) → C3(50,10): √((50−90)² + (10−50)²) = √(1600+1600) = √3200 = **56.5685**
    C3(50,10) → C4(10,50): √((10−50)² + (50−10)²) = √(1600+1600) = √3200 = **56.5685**
    C4(10,50) → Depot(50,50): √((50−10)² + (50−50)²) = √(1600+0) = √1600 = **40.0000**

Subtotal = 40.0000 + 56.5685 + 56.5685 + 56.5685 + 40.0000
**DA1 distance = 249.7056**

**Grand Total = DA1=249.7056 = 249.7056**

| | Distance |
|---|---|
| Brute Force Result | **249.7056** |
| Ground Truth | **249.71** |
| Verdict | **PROVEN GLOBAL OPTIMUM** |

---

## Map 02: map02_two_clusters

**Scenario:** 2 DAs · 8 customers · 2 clusters  
**Customers:** 8 | **DAs:** 2 | **Capacities:** DA1=4, DA2=4

### Search Space

| Metric | Value |
|---|---|
| Valid partitions checked | **70** |
| Route orderings checked | **40,320** |

### Optimal Solution Found

**DA1** (cap=4, load=4/4)
Route: Depot → C3 → C1 → C2 → C4 → Depot

    Depot(50,50) → C3(5,75): √((5−50)² + (75−50)²) = √(2025+625) = √2650 = **51.4782**
    C3(5,75) → C1(10,80): √((10−5)² + (80−75)²) = √(25+25) = √50 = **7.0711**
    C1(10,80) → C2(15,85): √((15−10)² + (85−80)²) = √(25+25) = √50 = **7.0711**
    C2(15,85) → C4(20,80): √((20−15)² + (80−85)²) = √(25+25) = √50 = **7.0711**
    C4(20,80) → Depot(50,50): √((50−20)² + (50−80)²) = √(900+900) = √1800 = **42.4264**

Subtotal = 51.4782 + 7.0711 + 7.0711 + 7.0711 + 42.4264
**DA1 distance = 115.1178**

**DA2** (cap=4, load=4/4)
Route: Depot → C6 → C5 → C8 → C7 → Depot

    Depot(50,50) → C6(90,20): √((90−50)² + (20−50)²) = √(1600+900) = √2500 = **50.0000**
    C6(90,20) → C5(85,15): √((85−90)² + (15−20)²) = √(25+25) = √50 = **7.0711**
    C5(85,15) → C8(90,10): √((90−85)² + (10−15)²) = √(25+25) = √50 = **7.0711**
    C8(90,10) → C7(80,10): √((80−90)² + (10−10)²) = √(100+0) = √100 = **10.0000**
    C7(80,10) → Depot(50,50): √((50−80)² + (50−10)²) = √(900+1600) = √2500 = **50.0000**

Subtotal = 50.0000 + 7.0711 + 7.0711 + 10.0000 + 50.0000
**DA2 distance = 124.1421**

**Grand Total = DA1=115.1178 + DA2=124.1421 = 239.2599**

| | Distance |
|---|---|
| Brute Force Result | **239.2599** |
| Ground Truth | **239.26** |
| Verdict | **PROVEN GLOBAL OPTIMUM** |

---

## Map 03: map03_three_sectors

**Scenario:** 3 DAs · 9 customers · 3 sectors  
**Customers:** 9 | **DAs:** 3 | **Capacities:** DA1=3, DA2=3, DA3=3

### Search Space

| Metric | Value |
|---|---|
| Valid partitions checked | **1,680** |
| Route orderings checked | **N/A (Held-Karp exact per group)** |

### Optimal Solution Found

**DA1** (cap=3, load=3/3)
Route: Depot → C2 → C1 → C3 → Depot

    Depot(50,50) → C2(5,80): √((5−50)² + (80−50)²) = √(2025+900) = √2925 = **54.0833**
    C2(5,80) → C1(10,90): √((10−5)² + (90−80)²) = √(25+100) = √125 = **11.1803**
    C1(10,90) → C3(20,85): √((20−10)² + (85−90)²) = √(100+25) = √125 = **11.1803**
    C3(20,85) → Depot(50,50): √((50−20)² + (50−85)²) = √(900+1225) = √2125 = **46.0977**

Subtotal = 54.0833 + 11.1803 + 11.1803 + 46.0977
**DA1 distance = 122.5417**

**DA2** (cap=3, load=3/3)
Route: Depot → C6 → C4 → C5 → Depot

    Depot(50,50) → C6(80,85): √((80−50)² + (85−50)²) = √(900+1225) = √2125 = **46.0977**
    C6(80,85) → C4(90,90): √((90−80)² + (90−85)²) = √(100+25) = √125 = **11.1803**
    C4(90,90) → C5(85,80): √((85−90)² + (80−90)²) = √(25+100) = √125 = **11.1803**
    C5(85,80) → Depot(50,50): √((50−85)² + (50−80)²) = √(1225+900) = √2125 = **46.0977**

Subtotal = 46.0977 + 11.1803 + 11.1803 + 46.0977
**DA2 distance = 114.5561**

**DA3** (cap=3, load=3/3)
Route: Depot → C9 → C7 → C8 → Depot

    Depot(50,50) → C9(60,8): √((60−50)² + (8−50)²) = √(100+1764) = √1864 = **43.1741**
    C9(60,8) → C7(50,5): √((50−60)² + (5−8)²) = √(100+9) = √109 = **10.4403**
    C7(50,5) → C8(40,10): √((40−50)² + (10−5)²) = √(100+25) = √125 = **11.1803**
    C8(40,10) → Depot(50,50): √((50−40)² + (50−10)²) = √(100+1600) = √1700 = **41.2311**

Subtotal = 43.1741 + 10.4403 + 11.1803 + 41.2311
**DA3 distance = 106.0258**

**Grand Total = DA1=122.5417 + DA2=114.5561 + DA3=106.0258 = 343.1236**

| | Distance |
|---|---|
| Brute Force Result | **343.1236** |
| Ground Truth | **343.12** |
| Verdict | **PROVEN GLOBAL OPTIMUM** |

---

## Map 04: map04_medium_balanced

**Scenario:** 2 DAs · 12 customers · balanced  
**Customers:** 12 | **DAs:** 2 | **Capacities:** DA1=6, DA2=6

### Search Space

| Metric | Value |
|---|---|
| Valid partitions checked | **924** |
| Route orderings checked | **1,330,560** |

### Optimal Solution Found

**DA1** (cap=6, load=6/6)
Route: Depot → C6 → C4 → C1 → C3 → C5 → C2 → Depot

    Depot(50,50) → C6(15,50): √((15−50)² + (50−50)²) = √(1225+0) = √1225 = **35.0000**
    C6(15,50) → C4(10,30): √((10−15)² + (30−50)²) = √(25+400) = √425 = **20.6155**
    C4(10,30) → C1(10,10): √((10−10)² + (10−30)²) = √(0+400) = √400 = **20.0000**
    C1(10,10) → C3(30,10): √((30−10)² + (10−10)²) = √(400+0) = √400 = **20.0000**
    C3(30,10) → C5(25,20): √((25−30)² + (20−10)²) = √(25+100) = √125 = **11.1803**
    C5(25,20) → C2(20,30): √((20−25)² + (30−20)²) = √(25+100) = √125 = **11.1803**
    C2(20,30) → Depot(50,50): √((50−20)² + (50−30)²) = √(900+400) = √1300 = **36.0555**

Subtotal = 35.0000 + 20.6155 + 20.0000 + 20.0000 + 11.1803 + 11.1803 + 36.0555
**DA1 distance = 154.0317**

**DA2** (cap=6, load=6/6)
Route: Depot → C12 → C9 → C11 → C8 → C10 → C7 → Depot

    Depot(50,50) → C12(75,55): √((75−50)² + (55−50)²) = √(625+25) = √650 = **25.4951**
    C12(75,55) → C9(90,70): √((90−75)² + (70−55)²) = √(225+225) = √450 = **21.2132**
    C9(90,70) → C11(85,75): √((85−90)² + (75−70)²) = √(25+25) = √50 = **7.0711**
    C11(85,75) → C8(80,90): √((80−85)² + (90−75)²) = √(25+225) = √250 = **15.8114**
    C8(80,90) → C10(70,90): √((70−80)² + (90−90)²) = √(100+0) = √100 = **10.0000**
    C10(70,90) → C7(70,70): √((70−70)² + (70−90)²) = √(0+400) = √400 = **20.0000**
    C7(70,70) → Depot(50,50): √((50−70)² + (50−70)²) = √(400+400) = √800 = **28.2843**

Subtotal = 25.4951 + 21.2132 + 7.0711 + 15.8114 + 10.0000 + 20.0000 + 28.2843
**DA2 distance = 127.8750**

**Grand Total = DA1=154.0317 + DA2=127.8750 = 281.9067**

| | Distance |
|---|---|
| Brute Force Result | **281.9067** |
| Ground Truth | **281.91** |
| Verdict | **PROVEN GLOBAL OPTIMUM** |

---

## Map 05: map05_linear_street

**Scenario:** 2 DAs · 6 customers · linear  
**Customers:** 6 | **DAs:** 2 | **Capacities:** DA1=3, DA2=3

### Search Space

| Metric | Value |
|---|---|
| Valid partitions checked | **20** |
| Route orderings checked | **720** |

### Optimal Solution Found

**DA1** (cap=3, load=3/3)
Route: Depot → C1 → C2 → C3 → Depot

    Depot(50,50) → C1(5,50): √((5−50)² + (50−50)²) = √(2025+0) = √2025 = **45.0000**
    C1(5,50) → C2(25,50): √((25−5)² + (50−50)²) = √(400+0) = √400 = **20.0000**
    C2(25,50) → C3(40,50): √((40−25)² + (50−50)²) = √(225+0) = √225 = **15.0000**
    C3(40,50) → Depot(50,50): √((50−40)² + (50−50)²) = √(100+0) = √100 = **10.0000**

Subtotal = 45.0000 + 20.0000 + 15.0000 + 10.0000
**DA1 distance = 90.0000**

**DA2** (cap=3, load=3/3)
Route: Depot → C4 → C5 → C6 → Depot

    Depot(50,50) → C4(60,50): √((60−50)² + (50−50)²) = √(100+0) = √100 = **10.0000**
    C4(60,50) → C5(75,50): √((75−60)² + (50−50)²) = √(225+0) = √225 = **15.0000**
    C5(75,50) → C6(95,50): √((95−75)² + (50−50)²) = √(400+0) = √400 = **20.0000**
    C6(95,50) → Depot(50,50): √((50−95)² + (50−50)²) = √(2025+0) = √2025 = **45.0000**

Subtotal = 10.0000 + 15.0000 + 20.0000 + 45.0000
**DA2 distance = 90.0000**

**Grand Total = DA1=90.0000 + DA2=90.0000 = 180.0000**

| | Distance |
|---|---|
| Brute Force Result | **180.0000** |
| Ground Truth | **180.0** |
| Verdict | **PROVEN GLOBAL OPTIMUM** |

---

## Map 06: map06_tight_cluster

**Scenario:** WEIRD · 2 DAs · 8 customers · extreme crowding  
**Customers:** 8 | **DAs:** 2 | **Capacities:** DA1=4, DA2=4

### Search Space

| Metric | Value |
|---|---|
| Valid partitions checked | **70** |
| Route orderings checked | **40,320** |

### Optimal Solution Found

**DA1** (cap=4, load=4/4)
Route: Depot → C2 → C7 → C1 → C4 → Depot

    Depot(50,50) → C2(5,7): √((5−50)² + (7−50)²) = √(2025+1849) = √3874 = **62.2415**
    C2(5,7) → C7(4,6): √((4−5)² + (6−7)²) = √(1+1) = √2 = **1.4142**
    C7(4,6) → C1(3,3): √((3−4)² + (3−6)²) = √(1+9) = √10 = **3.1623**
    C1(3,3) → C4(2,8): √((2−3)² + (8−3)²) = √(1+25) = √26 = **5.0990**
    C4(2,8) → Depot(50,50): √((50−2)² + (50−8)²) = √(2304+1764) = √4068 = **63.7809**

Subtotal = 62.2415 + 1.4142 + 3.1623 + 5.0990 + 63.7809
**DA1 distance = 135.6979**

**DA2** (cap=4, load=4/4)
Route: Depot → C3 → C8 → C5 → C6 → Depot

    Depot(50,50) → C3(8,2): √((8−50)² + (2−50)²) = √(1764+2304) = √4068 = **63.7809**
    C3(8,2) → C8(7,4): √((7−8)² + (4−2)²) = √(1+4) = √5 = **2.2361**
    C8(7,4) → C5(6,5): √((6−7)² + (5−4)²) = √(1+1) = √2 = **1.4142**
    C5(6,5) → C6(9,9): √((9−6)² + (9−5)²) = √(9+16) = √25 = **5.0000**
    C6(9,9) → Depot(50,50): √((50−9)² + (50−9)²) = √(1681+1681) = √3362 = **57.9828**

Subtotal = 63.7809 + 2.2361 + 1.4142 + 5.0000 + 57.9828
**DA2 distance = 130.4139**

**Grand Total = DA1=135.6979 + DA2=130.4139 = 266.1118**

| | Distance |
|---|---|
| Brute Force Result | **266.1118** |
| Ground Truth | **266.11** |
| Verdict | **PROVEN GLOBAL OPTIMUM** |

---

## Map 07: map07_outlier

**Scenario:** WEIRD · 2 DAs · 6 customers · extreme outlier  
**Customers:** 6 | **DAs:** 2 | **Capacities:** DA1=3, DA2=3

### Search Space

| Metric | Value |
|---|---|
| Valid partitions checked | **20** |
| Route orderings checked | **720** |

### Optimal Solution Found

**DA1** (cap=3, load=3/3)
Route: Depot → C1 → C6 → C4 → Depot

    Depot(50,50) → C1(45,55): √((45−50)² + (55−50)²) = √(25+25) = √50 = **7.0711**
    C1(45,55) → C6(99,99): √((99−45)² + (99−55)²) = √(2916+1936) = √4852 = **69.6563**
    C6(99,99) → C4(60,60): √((60−99)² + (60−99)²) = √(1521+1521) = √3042 = **55.1543**
    C4(60,60) → Depot(50,50): √((50−60)² + (50−60)²) = √(100+100) = √200 = **14.1421**

Subtotal = 7.0711 + 69.6563 + 55.1543 + 14.1421
**DA1 distance = 146.0238**

**DA2** (cap=3, load=3/3)
Route: Depot → C2 → C5 → C3 → Depot

    Depot(50,50) → C2(55,45): √((55−50)² + (45−50)²) = √(25+25) = √50 = **7.0711**
    C2(55,45) → C5(50,30): √((50−55)² + (30−45)²) = √(25+225) = √250 = **15.8114**
    C5(50,30) → C3(40,40): √((40−50)² + (40−30)²) = √(100+100) = √200 = **14.1421**
    C3(40,40) → Depot(50,50): √((50−40)² + (50−40)²) = √(100+100) = √200 = **14.1421**

Subtotal = 7.0711 + 15.8114 + 14.1421 + 14.1421
**DA2 distance = 51.1667**

**Grand Total = DA1=146.0238 + DA2=51.1667 = 197.1906**

| | Distance |
|---|---|
| Brute Force Result | **197.1906** |
| Ground Truth | **197.19** |
| Verdict | **PROVEN GLOBAL OPTIMUM** |

---

## Map 08: map08_one_sided

**Scenario:** WEIRD · 3 DAs · 9 customers · left side only  
**Customers:** 9 | **DAs:** 3 | **Capacities:** DA1=3, DA2=3, DA3=3

### Search Space

| Metric | Value |
|---|---|
| Valid partitions checked | **1,680** |
| Route orderings checked | **N/A (Held-Karp exact per group)** |

### Optimal Solution Found

**DA1** (cap=3, load=3/3)
Route: Depot → C2 → C7 → C1 → Depot

    Depot(50,50) → C2(10,30): √((10−50)² + (30−50)²) = √(1600+400) = √2000 = **44.7214**
    C2(10,30) → C7(10,20): √((10−10)² + (20−30)²) = √(0+100) = √100 = **10.0000**
    C7(10,20) → C1(5,10): √((5−10)² + (10−20)²) = √(25+100) = √125 = **11.1803**
    C1(5,10) → Depot(50,50): √((50−5)² + (50−10)²) = √(2025+1600) = √3625 = **60.2080**

Subtotal = 44.7214 + 10.0000 + 11.1803 + 60.2080
**DA1 distance = 126.1097**

**DA2** (cap=3, load=3/3)
Route: Depot → C8 → C3 → C6 → Depot

    Depot(50,50) → C8(20,40): √((20−50)² + (40−50)²) = √(900+100) = √1000 = **31.6228**
    C8(20,40) → C3(5,50): √((5−20)² + (50−40)²) = √(225+100) = √325 = **18.0278**
    C3(5,50) → C6(20,50): √((20−5)² + (50−50)²) = √(225+0) = √225 = **15.0000**
    C6(20,50) → Depot(50,50): √((50−20)² + (50−50)²) = √(900+0) = √900 = **30.0000**

Subtotal = 31.6228 + 18.0278 + 15.0000 + 30.0000
**DA2 distance = 94.6505**

**DA3** (cap=3, load=3/3)
Route: Depot → C5 → C4 → C9 → Depot

    Depot(50,50) → C5(5,90): √((5−50)² + (90−50)²) = √(2025+1600) = √3625 = **60.2080**
    C5(5,90) → C4(15,70): √((15−5)² + (70−90)²) = √(100+400) = √500 = **22.3607**
    C4(15,70) → C9(15,60): √((15−15)² + (60−70)²) = √(0+100) = √100 = **10.0000**
    C9(15,60) → Depot(50,50): √((50−15)² + (50−60)²) = √(1225+100) = √1325 = **36.4005**

Subtotal = 60.2080 + 22.3607 + 10.0000 + 36.4005
**DA3 distance = 128.9692**

**Grand Total = DA1=126.1097 + DA2=94.6505 + DA3=128.9692 = 349.7294**

| | Distance |
|---|---|
| Brute Force Result | **349.7294** |
| Ground Truth | **349.73** |
| Verdict | **PROVEN GLOBAL OPTIMUM** |

---

## Map 09: map09_star_pattern

**Scenario:** WEIRD · 2 DAs · 8 customers · equidistant star  
**Customers:** 8 | **DAs:** 2 | **Capacities:** DA1=4, DA2=4

### Search Space

| Metric | Value |
|---|---|
| Valid partitions checked | **70** |
| Route orderings checked | **N/A (Held-Karp exact per group)** |

### Optimal Solution Found

**DA1** (cap=4, load=4/4)
Route: Depot → C4 → C3 → C2 → C1 → Depot

    Depot(50,50) → C4(75,25): √((75−50)² + (25−50)²) = √(625+625) = √1250 = **35.3553**
    C4(75,25) → C3(85,50): √((85−75)² + (50−25)²) = √(100+625) = √725 = **26.9258**
    C3(85,50) → C2(75,75): √((75−85)² + (75−50)²) = √(100+625) = √725 = **26.9258**
    C2(75,75) → C1(50,85): √((50−75)² + (85−75)²) = √(625+100) = √725 = **26.9258**
    C1(50,85) → Depot(50,50): √((50−50)² + (50−85)²) = √(0+1225) = √1225 = **35.0000**

Subtotal = 35.3553 + 26.9258 + 26.9258 + 26.9258 + 35.0000
**DA1 distance = 151.1328**

**DA2** (cap=4, load=4/4)
Route: Depot → C8 → C7 → C6 → C5 → Depot

    Depot(50,50) → C8(25,75): √((25−50)² + (75−50)²) = √(625+625) = √1250 = **35.3553**
    C8(25,75) → C7(15,50): √((15−25)² + (50−75)²) = √(100+625) = √725 = **26.9258**
    C7(15,50) → C6(25,25): √((25−15)² + (25−50)²) = √(100+625) = √725 = **26.9258**
    C6(25,25) → C5(50,15): √((50−25)² + (15−25)²) = √(625+100) = √725 = **26.9258**
    C5(50,15) → Depot(50,50): √((50−50)² + (50−15)²) = √(0+1225) = √1225 = **35.0000**

Subtotal = 35.3553 + 26.9258 + 26.9258 + 26.9258 + 35.0000
**DA2 distance = 151.1328**

**Grand Total = DA1=151.1328 + DA2=151.1328 = 302.2656**

| | Distance |
|---|---|
| Brute Force Result | **302.2656** |
| Ground Truth | **302.27** |
| Verdict | **PROVEN GLOBAL OPTIMUM** |

---

## Map 10: map10_unequal_demand

**Scenario:** WEIRD · 3 DAs · 10 customers · demand 1-3  
**Customers:** 10 | **DAs:** 3 | **Capacities:** DA1=7, DA2=7, DA3=7

### Search Space

| Metric | Value |
|---|---|
| Valid partitions checked | **2,700** |
| Route orderings checked | **N/A (Held-Karp exact per group)** |

### Optimal Solution Found

**DA1** (cap=7, load=6/7)
Route: Depot → C9 → C7 → C4 → Depot

    Depot(50,50) → C9(40,60): √((40−50)² + (60−50)²) = √(100+100) = √200 = **14.1421**
    C9(40,60) → C7(30,70): √((30−40)² + (70−60)²) = √(100+100) = √200 = **14.1421**
    C7(30,70) → C4(70,30): √((70−30)² + (30−70)²) = √(1600+1600) = √3200 = **56.5685**
    C4(70,30) → Depot(50,50): √((50−70)² + (50−30)²) = √(400+400) = √800 = **28.2843**

Subtotal = 14.1421 + 14.1421 + 56.5685 + 28.2843
**DA1 distance = 113.1371**

**DA2** (cap=7, load=7/7)
Route: Depot → C6 → C10 → C5 → Depot

    Depot(50,50) → C6(60,80): √((60−50)² + (80−50)²) = √(100+900) = √1000 = **31.6228**
    C6(60,80) → C10(90,90): √((90−60)² + (90−80)²) = √(900+100) = √1000 = **31.6228**
    C10(90,90) → C5(80,60): √((80−90)² + (60−90)²) = √(100+900) = √1000 = **31.6228**
    C5(80,60) → Depot(50,50): √((50−80)² + (50−60)²) = √(900+100) = √1000 = **31.6228**

Subtotal = 31.6228 + 31.6228 + 31.6228 + 31.6228
**DA2 distance = 126.4911**

**DA3** (cap=7, load=7/7)
Route: Depot → C8 → C2 → C1 → C3 → Depot

    Depot(50,50) → C8(50,20): √((50−50)² + (20−50)²) = √(0+900) = √900 = **30.0000**
    C8(50,20) → C2(30,20): √((30−50)² + (20−20)²) = √(400+0) = √400 = **20.0000**
    C2(30,20) → C1(10,10): √((10−30)² + (10−20)²) = √(400+100) = √500 = **22.3607**
    C1(10,10) → C3(20,40): √((20−10)² + (40−10)²) = √(100+900) = √1000 = **31.6228**
    C3(20,40) → Depot(50,50): √((50−20)² + (50−40)²) = √(900+100) = √1000 = **31.6228**

Subtotal = 30.0000 + 20.0000 + 22.3607 + 31.6228 + 31.6228
**DA3 distance = 135.6062**

**Grand Total = DA1=113.1371 + DA2=126.4911 + DA3=135.6062 = 375.2344**

| | Distance |
|---|---|
| Brute Force Result | **375.2344** |
| Ground Truth | **375.23** |
| Verdict | **PROVEN GLOBAL OPTIMUM** |

---

## Summary

| Map | Name | Ground Truth | Brute Force | Partitions | Verdict |
|---|---|---|---|---|---|
| 01 | map01_tiny_compass | 249.71 | 249.7056 | 1 | PROVEN OPTIMUM |
| 02 | map02_two_clusters | 239.26 | 239.2599 | 70 | PROVEN OPTIMUM |
| 03 | map03_three_sectors | 343.12 | 343.1236 | 1,680 | PROVEN OPTIMUM |
| 04 | map04_medium_balanced | 281.91 | 281.9067 | 924 | PROVEN OPTIMUM |
| 05 | map05_linear_street | 180.0 | 180.0000 | 20 | PROVEN OPTIMUM |
| 06 | map06_tight_cluster | 266.11 | 266.1118 | 70 | PROVEN OPTIMUM |
| 07 | map07_outlier | 197.19 | 197.1906 | 20 | PROVEN OPTIMUM |
| 08 | map08_one_sided | 349.73 | 349.7294 | 1,680 | PROVEN OPTIMUM |
| 09 | map09_star_pattern | 302.27 | 302.2656 | 70 | PROVEN OPTIMUM |
| 10 | map10_unequal_demand | 375.23 | 375.2344 | 2,700 | PROVEN OPTIMUM |

**All 10 ground truth values are proven global optima.**
