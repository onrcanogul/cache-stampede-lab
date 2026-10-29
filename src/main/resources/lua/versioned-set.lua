-- Cached values start with the row version ("<version>|..."). Refuse to
-- overwrite a value that is already at the same or a newer version.
local current = redis.call('GET', KEYS[1])
if current then
  local currentVersion = tonumber(string.match(current, '^(%d+)|'))
  if currentVersion and currentVersion >= tonumber(ARGV[1]) then
    return 0
  end
end
redis.call('SET', KEYS[1], ARGV[2], 'PX', ARGV[3])
return 1
