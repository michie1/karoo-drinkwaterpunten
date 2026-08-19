# Data sources and licence

1. Water-point data is read from [OpenStreetMap](https://www.openstreetmap.org/copyright) through the [Overpass API](https://overpass-api.de).
2. The query selects nodes, ways, and relations tagged `amenity=drinking_water` or `drinking_water=yes` within the Netherlands. Features tagged with `access=private`, `access=no`, or `access=customers` are excluded.
3. The bundled and synced point database is OSM data and is distributed under the Open Database Licence 1.0. A copy is in `LICENSES/ODbL-1.0.txt`.
4. Required credit: `Data: © OpenStreetMap contributors (ODbL 1.0), fetched through Overpass API.`
5. The app code is separate from the point database and is available under the Apache License 2.0.
6. Corrections should be made in OpenStreetMap.
7. This project is not affiliated with or endorsed by OpenStreetMap, Overpass API, Hammerhead, or SRAM.
