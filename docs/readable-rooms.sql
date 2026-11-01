-- Run in MySQL Workbench after refreshing SCHEMAS > bt > Views.
SELECT ten_phong, nguoi_tham_gia, diem_rank, ruong_da_an, nguoi_thang
FROM bt.matches;

-- Passwords are filled after each successful login. Once all are verified,
-- the server automatically removes the legacy salt/password_hash columns.
SELECT username, password_plaintext, rank_points, wins, total_treasures, total_matches
FROM bt.accounts;
