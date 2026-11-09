import { spawnSync } from "node:child_process";
import { defineConfig } from "vite";
import jahia from "@jahia/vite-plugin";

export default defineConfig({
  plugins: [
    jahia({
      // This function is called every time a build succeeds in watch mode
      watchCallback() {
        spawnSync("yarn", ["watch:callback"], { stdio: "inherit", shell: true });
      },
    }),
  ],
});
