begin;

-- Rasterize pixel-pen strokes on the same 320x240 grid as the app. SVG keeps
-- the existing 800x600 paper format so smooth strokes and stationery still mix.
create function private.board_pixel_stroke_svg(p_stroke jsonb) returns text
language plpgsql immutable set search_path = '' as $$
declare
  pixels boolean[] := array_fill(false, array[76800], array[0]);
  point jsonb;
  target_x integer;
  target_y integer;
  previous_x integer;
  previous_y integer;
  x integer;
  y integer;
  dx integer;
  dy integer;
  step_x integer;
  step_y integer;
  error integer;
  doubled integer;
  brush integer := greatest(1, least(16, floor((p_stroke->>'size')::numeric / 2.5 + 0.5)::integer));
  brush_offset integer;
  px integer;
  py integer;
  min_x integer := 319;
  max_x integer := 0;
  min_y integer := 239;
  max_y integer := 0;
  run_start integer;
  result text := format('<g fill="%s" shape-rendering="crispEdges">', p_stroke->>'color');
begin
  brush_offset := (brush - 1) / 2;
  for point in select value from jsonb_array_elements(p_stroke->'points') loop
    target_x := greatest(0, least(319, floor((point->>0)::numeric / 2.5)::integer));
    target_y := greatest(0, least(239, floor((point->>1)::numeric / 2.5)::integer));
    x := coalesce(previous_x, target_x);
    y := coalesce(previous_y, target_y);
    dx := abs(target_x - x);
    dy := abs(target_y - y);
    step_x := case when x < target_x then 1 else -1 end;
    step_y := case when y < target_y then 1 else -1 end;
    error := dx - dy;
    loop
      for py in greatest(0, y - brush_offset)..least(239, y - brush_offset + brush - 1) loop
        for px in greatest(0, x - brush_offset)..least(319, x - brush_offset + brush - 1) loop
          pixels[py * 320 + px] := true;
          min_x := least(min_x, px); max_x := greatest(max_x, px);
          min_y := least(min_y, py); max_y := greatest(max_y, py);
        end loop;
      end loop;
      exit when x = target_x and y = target_y;
      doubled := error * 2;
      if doubled > -dy then error := error - dy; x := x + step_x; end if;
      if doubled < dx then error := error + dx; y := y + step_y; end if;
    end loop;
    previous_x := target_x;
    previous_y := target_y;
  end loop;

  for py in min_y..max_y loop
    px := min_x;
    while px <= max_x loop
      if pixels[py * 320 + px] then
        run_start := px;
        while px <= max_x and pixels[py * 320 + px] loop
          px := px + 1;
        end loop;
        result := result || format('<rect x="%s" y="%s" width="%s" height="2.5"/>',
          run_start * 2.5, py * 2.5, (px - run_start) * 2.5);
      else
        px := px + 1;
      end if;
    end loop;
  end loop;
  return result || '</g>';
end $$;

create or replace function private.board_drawing_preview(p_drawing jsonb) returns text
language plpgsql immutable set search_path = '' as $$
declare
  s jsonb;
  coords text;
  result text := '<svg xmlns="http://www.w3.org/2000/svg" width="800" height="600" viewBox="0 0 800 600"><rect width="800" height="600" fill="white"/>';
begin
  if p_drawing is null then return null; end if;
  perform private.board_validate_drawing(p_drawing);
  for s in select value from jsonb_array_elements(p_drawing->'strokes') loop
    if s->>'pen' = 'pixel' then
      result := result || private.board_pixel_stroke_svg(s);
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

revoke all on function private.board_pixel_stroke_svg(jsonb) from public, anon, authenticated;

-- Published previews are stored, so refresh them to match the renderer.
update private.board_posts
set drawing_preview = private.board_note_preview(drawing, nullif(stationery->'artwork', 'null'::jsonb))
where drawing is not null and drawing::text like '%"pixel"%';

commit;
