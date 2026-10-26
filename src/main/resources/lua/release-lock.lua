-- Release only when the lock still belongs to the caller. A plain DEL would
-- remove a lock that expired and was re-acquired by someone else.
if redis.call('GET', KEYS[1]) == ARGV[1] then
  return redis.call('DEL', KEYS[1])
end
return 0
