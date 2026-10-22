-- tag::migration[]
CREATE TABLE public.book (id bigint PRIMARY KEY, title text NOT NULL);

CREATE TRIGGER book_changes
AFTER INSERT OR UPDATE OR DELETE ON public.book
FOR EACH ROW EXECUTE FUNCTION public.micronaut_data_notify_change('book_changes', 'id');

CREATE TRIGGER book_truncate
AFTER TRUNCATE ON public.book
FOR EACH STATEMENT EXECUTE FUNCTION public.micronaut_data_notify_change('book_changes', 'id');

INSERT INTO public.book VALUES (1, 'Present before startup');
-- end::migration[]
