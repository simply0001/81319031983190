const values: Record<string, unknown> = {
  bodyModel: "wiiu",
  shaderType: "wiiu",
  simpleShaderLegacyColors: false,
  toonShaderOutline: false
};

export async function getPocketPassSetting(key: string): Promise<any> {
  return values[key] ?? null;
}
