SELECT
  w.world_id,
  w.flags,
  w.host,
  w.activity,
  w.location,
  CASE
    WHEN COALESCE(sc.cnt, 0) > 32767 THEN 32767
    ELSE COALESCE(sc.cnt, 0)
  END AS population
FROM worlds w
LEFT JOIN (
  SELECT world_id, COUNT(*) AS cnt
  FROM sessions
  GROUP BY world_id
) sc ON sc.world_id = w.world_id
WHERE w.enabled <> 0
ORDER BY w.sort_order ASC, w.world_id ASC
