import psycopg2

# Public connection string from Railway (proxy host + proxy port)
DSN = "postgresql://postgres:evdyPtCkIdvESdDkDZszBiwfprWJlhIg@thomas.proxy.rlwy.net:21043/railway"

conn = psycopg2.connect(DSN)
conn.autocommit = True
with open("init.sql", "r", encoding="utf-8") as f:
    sql = f.read()
cur = conn.cursor()
cur.execute(sql)          # psycopg2 runs all the semicolon-separated statements
print("init.sql applied — tables created")
cur.close()
conn.close()