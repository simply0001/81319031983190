import type * as THREE from "three";
import { MiiFavoriteColorVec3Table } from "../constants/ColorTables";

export const HAT_COLOURS_PROPERTY = "pocketpass_colours";
export const HAT_COLOUR_2_PROPERTY = "pocketpass_colour_2";
export const FAVORITE_COLOR_COUNT = 12;
export const WHITE_FAVORITE_COLOR = 10;

const UNCOLOURED: [number, number, number] = [1, 1, 1];

export type PocketPassHatColourLayout = {
  colours: 1 | 2;
  colour2: number;
};

function isFavoriteColor(value: unknown): value is number {
  return (
    typeof value === "number" &&
    Number.isInteger(value) &&
    value >= 0 &&
    value < FAVORITE_COLOR_COUNT
  );
}

function materialsOf(mesh: THREE.Mesh): THREE.Material[] {
  return Array.isArray(mesh.material) ? mesh.material : [mesh.material];
}

function modelColour2(material: THREE.Material): number {
  const value = Number(material.userData?.[HAT_COLOUR_2_PROPERTY]);
  return isFavoriteColor(value) ? value : WHITE_FAVORITE_COLOR;
}

export function isTwoColourHatMaterial(
  material: THREE.Material | null | undefined
): boolean {
  return Number(material?.userData?.[HAT_COLOURS_PROPERTY]) === 2;
}

export function hatColourLayout(
  model: THREE.Object3D
): PocketPassHatColourLayout {
  const layout: PocketPassHatColourLayout = {
    colours: 1,
    colour2: WHITE_FAVORITE_COLOR
  };
  model.traverse((node) => {
    const mesh = node as THREE.Mesh;
    if (!mesh.isMesh) return;
    for (const material of materialsOf(mesh)) {
      if (!isTwoColourHatMaterial(material)) continue;
      layout.colours = 2;
      layout.colour2 = modelColour2(material);
    }
  });
  return layout;
}

export function hatLayerColours(
  material: THREE.Material,
  colour1: ArrayLike<number>,
  chosenColour2: unknown
): [number, number, number][] {
  const colour2 = isFavoriteColor(chosenColour2)
    ? chosenColour2
    : modelColour2(material);
  return [
    [colour1[0], colour1[1], colour1[2]],
    [...MiiFavoriteColorVec3Table[colour2]] as [number, number, number],
    UNCOLOURED
  ];
}
