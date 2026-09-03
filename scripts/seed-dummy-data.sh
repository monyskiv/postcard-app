#!/bin/bash
set -e

API="http://localhost:8080/api"
EMAIL="${SEED_ADMIN_EMAIL:-admin1@example.com}"
PASSWORD="${SEED_ADMIN_PASSWORD:?Set SEED_ADMIN_PASSWORD env var before running this script}"
IMG_DIR="$HOME/Desktop/postcard-seed-images"

echo "Logging in..."
TOKEN=$(curl -s -X POST "$API/auth/login" -H "Content-Type: application/json" -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}" | jq -r .token)

if [ "$TOKEN" == "null" ] || [ -z "$TOKEN" ]; then
  echo "Login failed — check EMAIL/PASSWORD at the top of this script."
  exit 1
fi
echo "Logged in."

mkdir -p "$IMG_DIR"

# Download 4 reusable placeholder images once (varied seeds so they're not identical)
echo "Downloading placeholder images..."
for i in 1 2 3 4; do
  if [ ! -f "$IMG_DIR/sample$i.jpg" ]; then
    curl -s -o "$IMG_DIR/sample$i.jpg" "https://picsum.photos/seed/postcard$i/800/600"
  fi
done
echo "Images ready in $IMG_DIR"

# 12 made-up postcard records: title|year|author|description|color|location
declare -a POSTCARDS=(
  "Greetings from Lviv|1932|Unknown|A hand-tinted view of the old town square.|COLOR|Lviv, Ukraine"
  "Odesa Harbor at Dusk|1958|M. Kovalenko|Ships docked along the Black Sea coast.|BLACK_AND_WHITE|Odesa, Ukraine"
  "Kyiv Cathedral Steps|1971|Unknown|Wide steps leading up to a golden-domed cathedral.|BLACK_AND_WHITE|Kyiv, Ukraine"
  "Carpathian Mountain Village|1963|I. Petrenko|A small village nestled in green hills.|COLOR|Carpathian Mountains, Ukraine"
  "Chernivtsi University Hall|1948|Unknown|Ornate university building, front facade.|BLACK_AND_WHITE|Chernivtsi, Ukraine"
  "Kharkiv Freedom Square|1967|Unknown|A vast open square with fountains.|BLACK_AND_WHITE|Kharkiv, Ukraine"
  "Yalta Coastline|1976|S. Ivanenko|Coastal cliffs and a distant lighthouse.|COLOR|Yalta, Ukraine"
  "Poltava Market Day|1955|Unknown|A bustling outdoor market scene.|BLACK_AND_WHITE|Poltava, Ukraine"
  "Lviv Opera House|1980|Unknown|Grand exterior of the opera house at night.|COLOR|Lviv, Ukraine"
  "Dnipro River Bridge|1969|V. Shevchenko|A long bridge spanning the river at sunset.|COLOR|Dnipro, Ukraine"
  "Uzhhorod Castle View|1937|Unknown|Castle on a hill overlooking the town.|BLACK_AND_WHITE|Uzhhorod, Ukraine"
  "Zaporizhzhia Cossack Memorial|1961|Unknown|A statue commemorating Cossack history.|COLOR|Zaporizhzhia, Ukraine"
)

i=0
for entry in "${POSTCARDS[@]}"; do
  IFS='|' read -r TITLE YEAR AUTHOR DESC COLOR LOCATION <<< "$entry"
  i=$((i+1))
  FRONT_IMG="$IMG_DIR/sample$(( (i % 4) + 1 )).jpg"
  BACK_IMG="$IMG_DIR/sample$(( ((i+1) % 4) + 1 )).jpg"

  echo "Creating ($i/12): $TITLE"

  RESP=$(curl -s -X POST "$API/postcards" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d "{\"title\":\"$TITLE\",\"year\":$YEAR,\"author\":\"$AUTHOR\",\"description\":\"$DESC\",\"color\":\"$COLOR\",\"location\":\"$LOCATION\",\"frontImageUrl\":\"placeholder\",\"backImageUrl\":\"placeholder\"}")

  ID=$(echo "$RESP" | jq -r .id)

  if [ "$ID" == "null" ] || [ -z "$ID" ]; then
    echo "  Failed to create postcard. Response: $RESP"
    continue
  fi

  curl -s -X POST "$API/postcards/$ID/images" \
    -H "Authorization: Bearer $TOKEN" \
    -F "front=@$FRONT_IMG;type=image/jpeg" \
    -F "back=@$BACK_IMG;type=image/jpeg" > /dev/null

  echo "  Created and uploaded images for $ID"
done

echo "Done. 12 postcards seeded."