alter table "answer42"."analysis_results" add column "last_accessed_at" timestamp(6) with time zone;

alter table "answer42"."chat_messages" add column "last_edited_at" timestamp(6) with time zone;

alter table "answer42"."discovered_papers" add column "discovered_at" timestamp(6) with time zone;

alter table "answer42"."user_roles" add column "created_at" timestamp(6) with time zone;


