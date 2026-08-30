import FFLModuleFactory from "../external/ffl.js/ffl-emscripten.js";
import { initializeFFL } from "../external/ffl.js/ffl.js";
import {
  loadBodyModels,
  loadClothesTextures,
  loadHatModels
} from "../util/ModelLoader";

let fflModule: any;
let preparePromise: Promise<void> | null = null;

export const getPocketPassFFL = () => fflModule;

export function preparePocketPassFFL(): Promise<void> {
  if (preparePromise) return preparePromise;
  preparePromise = (async () => {
    fflModule = await FFLModuleFactory({
      locateFile: (path: string) => new URL(`/dist/${path}`, location.origin).href
    });

    await Promise.all([
      loadBodyModels("wiiu"),
      loadHatModels(),
      loadClothesTextures()
    ]);

    const response = await fetch("/FFLResHigh.dat", {
      cache: "force-cache",
      credentials: "same-origin"
    });
    if (!response.ok) {
      throw new Error(`FFL resource unavailable (${response.status})`);
    }
    fflModule = (await initializeFFL(response, fflModule)).module;
  })();
  return preparePromise;
}
