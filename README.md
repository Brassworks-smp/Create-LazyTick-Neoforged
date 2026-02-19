This is a NeoForge fork of the mod made for the Brassworks SMP, but anyone can use it.

<img width="496" height="149" alt="logo" src="https://github.com/user-attachments/assets/b437a026-1527-48b9-9bda-a49af8369c89" />

<p align="center">
<a href="https://modrinth.com/mod/createlazytick"><img src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3.2.0/assets/cozy/available/modrinth_vector.svg" alt="Modrinth Page"></a>
<a href="https://www.curseforge.com/minecraft/mc-mods/create-lazytick"><img src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3.2.0/assets/cozy/available/curseforge_vector.svg" alt="CurseForge Page"></a>
</p>


## Create: LazyTick ⚙️

---

A mod focused on **performance optimization for Create**.

By implementing "lazy ticking" and caching mechanisms, it significantly reduces redundant calculations and static overhead of functional components. This allows servers to handle **2–4 times more active production lines** compared to vanilla Create, as well as much larger semi-static structures.

---

### 🔧 Core Optimizations

#### 🧱 Logistic Component Optimization
- **Belts / Hoppers / Chutes / Depots**
- Reduces static overhead by 50%–95%.
- Reduces dynamic overhead by 40%–80% under load.
- Significant benefits for large-scale storage and sorting systems.

---

#### 🏭 Processing Component Optimization
- **Mechanical Crafters / Mechanical Mixers / Mechanical Saws / Basins**
- Introduces recipe caching mechanisms.
- Performance gains are extremely noticeable in environments with many recipes.
- Active overhead can be reduced by 70%–95%.
- In extreme cases, overhead drops from ~300µs to ~2–10µs.

---

#### 🤖 Interaction Component Optimization
- **Mechanical Arms / Deployers**
- Reduces frequency of detection and repetitive searches.
- Reduces overhead by 20%–60%.
- Better performance when interacting with belts.

---

#### 🌊 Fluid System Optimization
- Global fluid ticks are reduced to 1/k of the original (default is 1/5).
- Supports configurable scaling factors.
- Significant benefits in environments with massive pipe networks.

---

### 📊 Actual Results

In real-world server testing environments:

- Can support 2–3 times more full production lines.
- Semi-static structures can scale up to 5 times larger.
- Optimization strength increases as the data volume grows in high-recipe environments.

---

### ⚠️ Notes

- Due to the reduced tick frequency, some device animations may show slight delays (adjustable via config).
- Some logic special-casing has been modified; structures relying on vanilla tick synchronization may need to be adapted using the "LazyTick Clock."
- It is recommended to test and backup your world before deploying to a production server.
