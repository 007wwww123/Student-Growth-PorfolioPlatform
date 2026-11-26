"""Issue one scoped session using the operator's authenticated mysql CLI."""
import argparse
import hashlib
import json
import re
import secrets
import subprocess


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--database", required=True)
    parser.add_argument("--role", choices=["STUDENT", "MENTOR", "COUNSELOR", "ADMIN"], required=True)
    parser.add_argument("--actor", type=int, required=True)
    parser.add_argument("--students", type=int, nargs="+", required=True)
    parser.add_argument("--years", nargs="+", required=True)
    parser.add_argument("--hours", type=int, required=True)
    args = parser.parse_args()
    if not all(0 < value <= 2**63 - 1 for value in [args.actor, *args.students]):
        parser.error("actor and student IDs must be positive BIGINTs")
    if args.role == "STUDENT" and set(args.students) != {args.actor}:
        parser.error("a student session must be limited to its actor ID")
    if not all(re.fullmatch(r"[0-9]{4}-[0-9]{4}-[12]", year) for year in args.years):
        parser.error("expected years in YYYY-YYYY-1 or YYYY-YYYY-2 format")
    if not 1 <= args.hours <= 24:
        parser.error("session lifetime must be between 1 and 24 hours")
    token = secrets.token_urlsafe(32)
    digest = hashlib.sha256(token.encode("ascii")).hexdigest()
    statement = (
        "INSERT INTO arc_session(token_hash, actor_id, role_code, student_ids, academic_years, expires_at) "
        f"VALUES ('{digest}', {args.actor}, '{args.role}', '{json.dumps(args.students)}', "
        f"'{json.dumps(args.years)}', CURRENT_TIMESTAMP + INTERVAL {args.hours} HOUR);"
    )
    subprocess.run(["mysql", "--database=" + args.database], input=statement, text=True, check=True)
    print(token)


if __name__ == "__main__":
    main()
