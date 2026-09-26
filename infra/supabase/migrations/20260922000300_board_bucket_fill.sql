begin;

-- Bucket fills are single undoable strokes containing pixel-aligned horizontal
-- runs. Each pair of points is the inclusive start and exclusive end of a row.
create or replace function private.board_validate_drawing(p_drawing jsonb) returns void
language plpgsql immutable set search_path='' as $$
declare
  s jsonb;
  p jsonb;
  points integer := 0;
  i integer;
  start_point jsonb;
  end_point jsonb;
begin
  if p_drawing is null then return; end if;
  if jsonb_typeof(p_drawing)<>'object' or p_drawing->>'version' is distinct from '1'
    or p_drawing->>'width' is distinct from '800' or p_drawing->>'height' is distinct from '600'
    or jsonb_typeof(p_drawing->'strokes') is distinct from 'array' or octet_length(p_drawing::text)>524288 then
    raise exception 'Invalid drawing document' using errcode='22023';
  end if;
  if jsonb_array_length(p_drawing->'strokes')>1000 then raise exception 'This note has too many strokes' using errcode='22023'; end if;
  if p_drawing - array['version','width','height','strokes'] <> '{}'::jsonb then raise exception 'Unsupported drawing fields' using errcode='22023'; end if;
  for s in select value from jsonb_array_elements(p_drawing->'strokes') loop
    if s - array['pen','color','size','points'] <> '{}'::jsonb then raise exception 'Unsupported drawing stroke fields' using errcode='22023'; end if;
    if coalesce(s->>'pen','') not in ('pixel','smooth','eraser','bucket') or coalesce(s->>'color','') not in
      ('#222222','#E84A5F','#F6B93B','#4CAF70','#3379D6','#9564C8','#FFFFFF')
      or jsonb_typeof(s->'size') is distinct from 'number' or (s->>'size')::numeric not between 1 and 40
      or jsonb_typeof(s->'points') is distinct from 'array' then raise exception 'Invalid drawing stroke' using errcode='22023'; end if;
    points:=points+jsonb_array_length(s->'points');
    if points>20000 or jsonb_array_length(s->'points')<1 then raise exception 'Invalid drawing point count' using errcode='22023'; end if;
    for p in select value from jsonb_array_elements(s->'points') loop
      if jsonb_typeof(p)<>'array' or jsonb_array_length(p)<>2 or jsonb_typeof(p->0)<>'number' or jsonb_typeof(p->1)<>'number'
        or (p->>0)::numeric not between 0 and 800 or (p->>1)::numeric not between 0 and 600 then
        raise exception 'Drawing points must stay on the paper' using errcode='22023';
      end if;
    end loop;
    if s->>'pen'='bucket' then
      if (s->>'size')::numeric<>2 or mod(jsonb_array_length(s->'points'),2)<>0 then
        raise exception 'Invalid bucket fill' using errcode='22023';
      end if;
      for i in 0..(jsonb_array_length(s->'points') / 2 - 1) loop
        start_point:=s->'points'->(i*2);
        end_point:=s->'points'->(i*2+1);
        if (start_point->>1)::numeric<>(end_point->>1)::numeric
          or (start_point->>0)::numeric >= (end_point->>0)::numeric
          or (start_point->>1)::numeric >= 600
          or mod((start_point->>0)::numeric,2.5)<>0 or mod((end_point->>0)::numeric,2.5)<>0
          or mod((start_point->>1)::numeric,2.5)<>0 then
          raise exception 'Invalid bucket run' using errcode='22023';
        end if;
      end loop;
    end if;
  end loop;
end $$;

create or replace function private.board_drawing_preview(p_drawing jsonb) returns text
language plpgsql immutable set search_path = '' as $$
declare
  s jsonb;
  coords text;
  i integer;
  start_point jsonb;
  end_point jsonb;
  result text := '<svg xmlns="http://www.w3.org/2000/svg" width="800" height="600" viewBox="0 0 800 600"><rect width="800" height="600" fill="white"/>';
begin
  if p_drawing is null then return null; end if;
  perform private.board_validate_drawing(p_drawing);
  for s in select value from jsonb_array_elements(p_drawing->'strokes') loop
    if s->>'pen' = 'pixel' then
      result := result || private.board_pixel_stroke_svg(s);
      continue;
    end if;
    if s->>'pen' = 'bucket' then
      result := result || format('<g fill="%s" shape-rendering="crispEdges">', s->>'color');
      for i in 0..(jsonb_array_length(s->'points') / 2 - 1) loop
        start_point:=s->'points'->(i*2);
        end_point:=s->'points'->(i*2+1);
        result := result || format('<rect x="%s" y="%s" width="%s" height="2.5"/>',
          start_point->>0, start_point->>1, (end_point->>0)::numeric-(start_point->>0)::numeric);
      end loop;
      result := result || '</g>';
      continue;
    end if;
    if jsonb_array_length(s->'points') = 1 then
      result := result || format('<circle cx="%s" cy="%s" r="%s" fill="%s"/>',
        s->'points'->0->>0, s->'points'->0->>1, (s->>'size')::numeric / 2,
        case when s->>'pen' = 'eraser' then '#FFFFFF' else s->>'color' end);
      continue;
    end if;
    select string_agg((value->>0)||','||(value->>1), ' ' order by ordinality)
      into coords from jsonb_array_elements(s->'points') with ordinality;
    result := result || format('<polyline points="%s %s" fill="none" stroke="%s" stroke-width="%s" stroke-linecap="round" stroke-linejoin="round"/>',
      (s->'points'->0->>0)||','||(s->'points'->0->>1), coords,
      case when s->>'pen' = 'eraser' then '#FFFFFF' else s->>'color' end, s->>'size');
  end loop;
  return result || '</svg>';
end $$;

commit;
