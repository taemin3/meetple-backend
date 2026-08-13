UPDATE meetings
SET end_date = meeting_date + INTERVAL '2' HOUR
WHERE end_date IS NULL;
