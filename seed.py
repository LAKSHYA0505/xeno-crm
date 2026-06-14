import psycopg2
import random
import json
from faker import Faker
from datetime import datetime, timedelta
import uuid

fake = Faker('en_IN')
random.seed(42)

# --- DB CONNECTION ---
conn = psycopg2.connect(
    host="thomas.proxy.rlwy.net",
    database="railway",
    user="postgres",
    password="evdyPtCkIdvESdDkDZszBiwfprWJlhIg",
    port=21043
)
cur = conn.cursor()

# ----------------------------
# BRAND: SoleStreet (D2C Footwear)
# ----------------------------

PRODUCTS = [
    {"name": "Nike Air Max 270",        "category": "running",   "price": 8995},
    {"name": "Adidas Stan Smith",       "category": "casual",    "price": 6995},
    {"name": "Puma Suede Classic",      "category": "casual",    "price": 5495},
    {"name": "Nike Revolution 6",       "category": "running",   "price": 4995},
    {"name": "Adidas Ultraboost 22",    "category": "running",   "price": 12995},
    {"name": "Reebok Classic Leather",  "category": "casual",    "price": 6495},
    {"name": "Nike Air Force 1",        "category": "lifestyle", "price": 7995},
    {"name": "Puma RS-X",               "category": "lifestyle", "price": 7495},
    {"name": "Adidas NMD R1",           "category": "lifestyle", "price": 10995},
    {"name": "New Balance 574",         "category": "casual",    "price": 8495},
    {"name": "Nike Pegasus 40",         "category": "running",   "price": 9495},
    {"name": "Skechers Go Walk",        "category": "comfort",   "price": 3995},
    {"name": "Adidas Forum Low",        "category": "lifestyle", "price": 7295},
    {"name": "Puma Cali Sport",         "category": "lifestyle", "price": 6295},
    {"name": "Nike Blazer Mid 77",      "category": "lifestyle", "price": 8295},
    {"name": "Adidas Gazelle",          "category": "casual",    "price": 7995},
    {"name": "New Balance 990",         "category": "running",   "price": 13995},
    {"name": "Converse Chuck Taylor",   "category": "casual",    "price": 4995},
    {"name": "Vans Old Skool",          "category": "casual",    "price": 5995},
    {"name": "Nike Dunk Low",           "category": "lifestyle", "price": 8995},
]

CITIES = [
    "Mumbai", "Delhi", "Bangalore", "Hyderabad", "Chennai",
    "Pune", "Kolkata", "Ahmedabad", "Jaipur", "Lucknow",
    "Surat", "Nagpur", "Indore", "Bhopal", "Noida",
    "Gurgaon", "Chandigarh", "Kochi", "Coimbatore", "Vizag"
]

CHANNELS = ["website", "instagram", "app", "whatsapp_store"]

GENDERS = ["male", "female", "other"]
GENDER_WEIGHTS = [45, 50, 5]

NOW = datetime.now()


def random_date_between(start_days_ago, end_days_ago):
    """Returns a datetime between end_days_ago and start_days_ago from now."""
    days = random.randint(end_days_ago, start_days_ago)
    # subtract random hours/minutes for realism
    return NOW - timedelta(
        days=days,
        hours=random.randint(0, 23),
        minutes=random.randint(0, 59)
    )


def make_order_items():
    """Generate 1-2 items for an order. Returns (items_list, total_amount)."""
    count = random.randint(1, 2)
    chosen = random.sample(PRODUCTS, count)
    items = []
    for p in chosen:
        items.append({
            "name":     p["name"],
            "category": p["category"],
            "quantity": 1,
            "price":    p["price"]
        })
    total = sum(i["price"] * i["quantity"] for i in items)
    return items, total


def generate_order_dates(num_orders, last_order_at):
    """
    Given number of orders and the most recent order date,
    generate all order dates spread realistically in the past.
    """
    dates = [last_order_at]
    for _ in range(num_orders - 1):
        # each earlier order is 30-300 days before the previous one
        prev = dates[-1]
        earlier = prev - timedelta(
            days=random.randint(30, 300),
            hours=random.randint(0, 23)
        )
        dates.append(earlier)
    dates.sort()  # chronological order
    return dates


# ----------------------------
# BEHAVIORAL SEGMENTS
# ----------------------------
# Each config defines:
#   label           - for logging
#   count           - number of customers
#   order_range     - (min, max) number of orders
#   last_order_days - (max_days_ago, min_days_ago) for most recent order
#                     NOTE: max_days_ago > min_days_ago
# ----------------------------

SEGMENT_CONFIGS = [
    {
        "label":           "recent_active",
        "count":           160,
        "order_range":     (2, 4),
        "last_order_days": (30, 1),      # ordered 1-30 days ago
    },
    {
        "label":           "lapsing",
        "count":           280,
        "order_range":     (2, 5),
        "last_order_days": (90, 31),     # ordered 31-90 days ago
    },
    {
        "label":           "inactive",
        "count":           240,
        "order_range":     (1, 3),
        "last_order_days": (365, 91),    # ordered 91-365 days ago
    },
    {
        "label":           "new_single",
        "count":           80,
        "order_range":     (1, 1),       # exactly 1 order
        "last_order_days": (45, 1),      # recent
    },
    {
        "label":           "high_value",
        "count":           40,
        "order_range":     (5, 8),       # many orders
        "last_order_days": (60, 5),      # still active
    },
]

# ----------------------------
# SEED
# ----------------------------

print("=" * 50)
print("SoleStreet CRM - Seed Script")
print("=" * 50)

print("\nClearing existing data...")
cur.execute("DELETE FROM campaign_events")
cur.execute("DELETE FROM campaign_logs")
cur.execute("DELETE FROM campaigns")
cur.execute("DELETE FROM segment_customers")
cur.execute("DELETE FROM segments")
cur.execute("DELETE FROM orders")
cur.execute("DELETE FROM customers")
conn.commit()
print("Done.")

total_customers_inserted = 0
total_orders_inserted = 0

for seg in SEGMENT_CONFIGS:
    label         = seg["label"]
    count         = seg["count"]
    order_range   = seg["order_range"]
    max_days_ago  = seg["last_order_days"][0]
    min_days_ago  = seg["last_order_days"][1]

    print(f"\nSeeding [{label}] - {count} customers...")

    for i in range(count):
        customer_id = str(uuid.uuid4())

        # --- Customer fields ---
        name   = fake.name()
        email  = fake.unique.email()
        phone  = "9" + str(random.randint(100000000, 999999999))
        city   = random.choice(CITIES)
        gender = random.choices(GENDERS, weights=GENDER_WEIGHTS, k=1)[0]

        # account created well before their first order
        created_at = NOW - timedelta(days=random.randint(max_days_ago + 30, max_days_ago + 730))

        # --- Order dates ---
        num_orders    = random.randint(*order_range)
        last_order_at = random_date_between(max_days_ago, min_days_ago)
        order_dates   = generate_order_dates(num_orders, last_order_at)

        # --- Insert customer ---
        cur.execute("""
            INSERT INTO customers (id, name, email, phone, city, gender, created_at)
            VALUES (%s, %s, %s, %s, %s, %s, %s)
        """, (customer_id, name, email, phone, city, gender, created_at))

        # --- Insert orders ---
        for odate in order_dates:
            items, amount = make_order_items()
            cur.execute("""
                INSERT INTO orders (id, customer_id, amount, items, channel, ordered_at, created_at)
                VALUES (%s, %s, %s, %s, %s, %s, %s)
            """, (
                str(uuid.uuid4()),
                customer_id,
                amount,
                json.dumps(items),
                random.choice(CHANNELS),
                odate,
                odate + timedelta(seconds=random.randint(1, 60))
            ))
            total_orders_inserted += 1

        total_customers_inserted += 1

    conn.commit()
    print(f"  OK: {count} customers committed.")

# ----------------------------
# VERIFICATION
# ----------------------------

print("\n" + "=" * 50)
print("VERIFICATION")
print("=" * 50)

cur.execute("SELECT COUNT(*) FROM customers")
print(f"\nTotal customers : {cur.fetchone()[0]}")

cur.execute("SELECT COUNT(*) FROM orders")
print(f"Total orders    : {cur.fetchone()[0]}")

print("\nGender distribution:")
cur.execute("SELECT gender, COUNT(*) FROM customers GROUP BY gender ORDER BY COUNT(*) DESC")
for row in cur.fetchall():
    print(f"  {row[0]:<10} {row[1]}")

print("\nCity distribution (top 5):")
cur.execute("SELECT city, COUNT(*) FROM customers GROUP BY city ORDER BY COUNT(*) DESC LIMIT 5")
for row in cur.fetchall():
    print(f"  {row[0]:<15} {row[1]}")

print("\nBehavioral segment distribution:")
cur.execute("""
    SELECT bucket, COUNT(*) AS customers
    FROM (
        SELECT
            c.id,
            CASE
                WHEN MAX(o.ordered_at) >= NOW() - INTERVAL '30 days'  THEN 'recent_active (0-30d)'
                WHEN MAX(o.ordered_at) >= NOW() - INTERVAL '90 days'  THEN 'lapsing      (31-90d)'
                WHEN MAX(o.ordered_at) >= NOW() - INTERVAL '365 days' THEN 'inactive     (91-365d)'
                ELSE                                                        'very_old     (365d+)'
            END AS bucket
        FROM customers c
        JOIN orders o ON o.customer_id = c.id
        GROUP BY c.id
    ) t
    GROUP BY bucket
    ORDER BY customers DESC
""")
for row in cur.fetchall():
    print(f"  {row[0]}  ->  {row[1]} customers")

print("\nOrder count distribution:")
cur.execute("""
    SELECT order_count, COUNT(*) AS customers
    FROM (
        SELECT customer_id, COUNT(*) AS order_count
        FROM orders
        GROUP BY customer_id
    ) t
    GROUP BY order_count
    ORDER BY order_count
""")
for row in cur.fetchall():
    print(f"  {row[0]} order(s)  ->  {row[1]} customers")

print("\nSpend stats (computed from orders):")
cur.execute("""
    SELECT
        ROUND(AVG(total)::numeric, 2) AS avg_spend,
        MAX(total)                    AS max_spend,
        MIN(total)                    AS min_spend
    FROM (
        SELECT customer_id, SUM(amount) AS total
        FROM orders
        GROUP BY customer_id
    ) t
""")
row = cur.fetchone()
print(f"  avg Rs.{row[0]}   max Rs.{row[1]}   min Rs.{row[2]}")

print("\nSample customers (5 rows):")
cur.execute("""
    SELECT c.name, c.city, c.gender, COUNT(o.id) AS orders,
           ROUND(SUM(o.amount)::numeric, 0) AS total_spent,
           MAX(o.ordered_at)::date AS last_order
    FROM customers c
    JOIN orders o ON o.customer_id = c.id
    GROUP BY c.id, c.name, c.city, c.gender
    ORDER BY RANDOM()
    LIMIT 5
""")
print(f"  {'Name':<25} {'City':<15} {'Gender':<8} {'Orders':<8} {'Spent':>10}  {'Last Order'}")
print("  " + "-" * 80)
for row in cur.fetchall():
    print(f"  {row[0]:<25} {row[1]:<15} {row[2]:<8} {row[3]:<8} Rs.{row[4]:>8}  {row[5]}")

cur.close()
conn.close()

print("\n" + "=" * 50)
print("Seed complete.")
print("=" * 50)