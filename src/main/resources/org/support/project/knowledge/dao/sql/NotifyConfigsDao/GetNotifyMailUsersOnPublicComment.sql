SELECT * FROM (
SELECT
        USERS.*
    FROM
        USERS INNER JOIN NOTIFY_CONFIGS
            ON (USERS.USER_ID = NOTIFY_CONFIGS.USER_ID)
WHERE
    NOTIFY_CONFIGS.TO_ITEM_COMMENT = 1
    AND (NOTIFY_CONFIGS.TO_ITEM_IGNORE_PUBLIC != 1 OR NOTIFY_CONFIGS.TO_ITEM_IGNORE_PUBLIC IS NULL)
    AND NOTIFY_CONFIGS.NOTIFY_MAIL = 1
    AND USERS.DELETE_FLAG = 0
ORDER BY USERS.INSERT_USER
) AS RESULT
LIMIT ? OFFSET ?
