# VSOP87D source series

`mercury.csv` … `neptune.csv` are the complete VSOP87D series (heliocentric
spherical coordinates, mean ecliptic and equinox of date) for the eight major
planets, including Earth. Each line is one periodic term:

```text
L0 440250710.144000 0.00000000000 0.00000000000
│  │                │               └─ C: argument rate, radians per Julian millennium
│  │                └───────────────── B: phase at J2000, radians
│  └────────────────────────────────── A: amplitude in units of 1e-8 rad (1e-8 AU for R)
└───────────────────────────────────── coordinate and power of t
```

`L` is heliocentric longitude, `B` heliocentric latitude and `R` the
heliocentric distance. The series are grouped by power of the time argument
`t = (JD − 2451545.0) / 365250` and must be combined as
`Σ A·cos(B + C·t)·t^k`.

## Provenance

The values are the official CDS catalogue `VI/81` VSOP87D tables as shipped by
the pure-Python [PyMeeus](https://github.com/architest/pymeeus) package
(`pymeeus` 0.5.x, `VSOP87_L`/`VSOP87_B`/`VSOP87_R`). They were extracted verbatim
into CSV, so the committed data is the authoritative set and no network access
is needed to rebuild the tables. Regenerating the CSVs from scratch requires
PyMeeus and reproduces exactly the files in this directory:

```python
import os
from pymeeus import Mercury, Venus, Earth, Mars, Jupiter, Saturn, Uranus, Neptune

for name, body in {
    "mercury": Mercury, "venus": Venus, "earth": Earth, "mars": Mars,
    "jupiter": Jupiter, "saturn": Saturn, "uranus": Uranus, "neptune": Neptune,
}.items():
    with open(f"{name}.csv", "w") as out:
        for coordinate, series in (("L", body.VSOP87_L), ("B", body.VSOP87_B), ("R", body.VSOP87_R)):
            for power, terms in enumerate(series):
                for amplitude, phase, frequency in terms:
                    out.write(f"{coordinate}{power} {amplitude:.6f} {phase:.11f} {frequency:.11f}\n")
```

`tools/Vsop87Generator.java` truncates these tables by amplitude and writes the
coefficient tables compiled into the companion and the shader pack. The
committed outputs, not these CSVs, are what the build consumes.
