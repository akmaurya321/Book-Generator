DO $$
DECLARE
    user_id_type text;
    constraint_name text;
BEGIN
    IF to_regclass('public.app_users') IS NULL
            OR to_regclass('public.app_user_roles') IS NULL THEN
        RETURN;
    END IF;

    SELECT data_type
    INTO user_id_type
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name = 'app_user_roles'
      AND column_name = 'user_id';

    IF user_id_type IS NULL THEN
        RETURN;
    END IF;

    IF user_id_type <> 'uuid' THEN
        IF user_id_type <> 'character varying' THEN
            RAISE EXCEPTION 'Unsupported app_user_roles.user_id type: %', user_id_type;
        END IF;

        IF EXISTS (
            SELECT 1
            FROM public.app_user_roles
            WHERE user_id !~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
        ) THEN
            RAISE EXCEPTION 'Cannot convert app_user_roles.user_id: malformed UUID values exist';
        END IF;

        IF EXISTS (
            SELECT 1
            FROM public.app_user_roles roles
            LEFT JOIN public.app_users users ON users.id = roles.user_id::uuid
            WHERE users.id IS NULL
        ) THEN
            RAISE EXCEPTION 'Cannot convert app_user_roles.user_id: role rows reference missing users';
        END IF;

        FOR constraint_name IN
            SELECT conname
            FROM pg_constraint
            WHERE conrelid = 'public.app_user_roles'::regclass
              AND contype = 'f'
        LOOP
            EXECUTE format('ALTER TABLE public.app_user_roles DROP CONSTRAINT %I', constraint_name);
        END LOOP;

        ALTER TABLE public.app_user_roles
            ALTER COLUMN user_id TYPE uuid
            USING user_id::uuid;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'public.app_user_roles'::regclass
          AND confrelid = 'public.app_users'::regclass
          AND contype = 'f'
    ) THEN
        ALTER TABLE public.app_user_roles
            ADD CONSTRAINT fk_app_user_roles_app_users
            FOREIGN KEY (user_id) REFERENCES public.app_users(id) ON DELETE CASCADE;
    END IF;
END $$;