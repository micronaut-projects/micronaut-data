-- tag::statement-triggers[]
DROP TRIGGER IF EXISTS book_changes ON public.book;
DROP TRIGGER IF EXISTS book_truncate ON public.book;

CREATE TRIGGER book_changes
AFTER INSERT OR UPDATE OR DELETE OR TRUNCATE ON public.book
FOR EACH STATEMENT EXECUTE FUNCTION public.micronaut_data_notify_invalidation('book_changes');
-- end::statement-triggers[]
