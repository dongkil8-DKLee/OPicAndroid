"""
opic.db → Room 호환 DB 변환 스크립트

변경 사항 (데이터 보존, 컬럼명 동일):
1. DATETIME → TEXT (Room affinity 호환)
2. 컬럼/테이블명은 Python 원본 100% 유지
3. 데이터 무손실 복사
"""

import sqlite3
import shutil
import os

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
SRC_DB = os.path.join(SCRIPT_DIR, "..", "opic.db")
DST_DB = os.path.join(SCRIPT_DIR, "app", "src", "main", "assets", "opic.db")


def prepare():
    os.makedirs(os.path.dirname(DST_DB), exist_ok=True)

    # 원본 복사 후 수정 (데이터 보존)
    shutil.copy2(SRC_DB, DST_DB)

    conn = sqlite3.connect(DST_DB)
    c = conn.cursor()

    # ── 1) User_Study_Progress: last_modified DATETIME → TEXT ─────────
    c.execute("ALTER TABLE User_Study_Progress RENAME TO _usp_old")
    c.execute("""
        CREATE TABLE User_Study_Progress (
            progress_id INTEGER PRIMARY KEY,
            user_id INTEGER,
            question_id INTEGER,
            study_count INTEGER DEFAULT 0,
            last_modified TEXT,
            is_favorite INTEGER DEFAULT 0,
            stt_text TEXT,
            analysis_result TEXT,
            FOREIGN KEY(user_id) REFERENCES Users(user_id),
            FOREIGN KEY(question_id) REFERENCES Questions(question_id),
            UNIQUE (user_id, question_id)
        )
    """)
    c.execute("""
        INSERT INTO User_Study_Progress
            (progress_id, user_id, question_id, study_count,
             last_modified, is_favorite, stt_text, analysis_result)
        SELECT progress_id, user_id, question_id, study_count,
               last_modified, is_favorite, stt_text, analysis_result
        FROM _usp_old
    """)
    c.execute("DROP TABLE _usp_old")

    # ── 2) Test_Sessions: timestamp DATETIME → TEXT ───────────────────
    c.execute("ALTER TABLE Test_Sessions RENAME TO _ts_old")
    c.execute("""
        CREATE TABLE Test_Sessions (
            session_id INTEGER PRIMARY KEY,
            user_id INTEGER,
            timestamp TEXT DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY(user_id) REFERENCES Users(user_id)
        )
    """)
    c.execute("""
        INSERT INTO Test_Sessions (session_id, user_id, timestamp)
        SELECT session_id, user_id, timestamp FROM _ts_old
    """)
    c.execute("DROP TABLE _ts_old")

    # ── 3) 검증 ──────────────────────────────────────────────────────
    tables = ["Users", "Questions", "Question_Slots",
              "User_Study_Progress", "Test_Sessions", "Test_Results", "Api_Keys"]
    for t in tables:
        c.execute(f'SELECT COUNT(*) FROM "{t}"')
        count = c.fetchone()[0]
        c.execute(f'PRAGMA table_info("{t}")')
        cols = c.fetchall()
        print(f"  {t}: {count} rows, {len(cols)} cols")
        for col in cols:
            print(f"    {col}")

    conn.commit()
    conn.close()
    print(f"\n✅ Room-compatible DB saved to: {DST_DB}")


if __name__ == "__main__":
    prepare()
