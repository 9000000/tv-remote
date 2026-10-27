'use strict';
// K3: the small 3D renderer of the web games (WebGL 1, so a Mali-450 TV box runs it). One shader: flat-shaded low-poly meshes with
// vertex colours, one sun, ambient light, distance fog and an additive tint (a hit flash); no textures, no shadows, no post effects.
// Built for weak TVs: light and fog are worked out per vertex (Utgard GPUs have no highp in the fragment shader, and that stage is
// where a weak GPU runs out first), the fragment shader only writes a colour; the canvas is drawn at ~3/4 resolution and stretched,
// and smaller still when Kit sees the frame rate drop (K.scale); a mesh outside the camera's view is skipped (its bounding sphere
// against the 6 planes); nothing is allocated per frame. Lessons from LittleJS's issues: transforms happen in the vertex shader, not
// on the CPU (#78); a fixed internal resolution, not devicePixelRatio (#164, #13: full 1080p ran 5-10 fps on a weak device).
//
//   K3.init({ res, fog, near, far, fov }) → false when there is no WebGL (the game says so instead of starting)
//   const g = K3.geo(); g.at(x, y, z, yaw); g.box(...); g.cyl(...); g.ball(...); g.quad(...); const mesh = g.build();
//   each frame, in the game's draw(c): K3.begin(cam); K3.draw(mesh, K3.place(m, x, y, z, yaw)); K3.cube(...); K3.end();
//   then Kit's 2D canvas (above the 3D one) draws the HUD as usual; K3.toScreen(x, y, z, out) puts a name tag over a kart.
// Units: metres, y up; yaw turns about y (0 looks down -z). Colours: '#rrggbb' or [r, g, b] in 0..255.
(function () {
  const K3 = { ok: false, res: 0.75, stats: { draws: 0, tris: 0, culled: 0, w: 0, h: 0 } };
  window.K3 = K3;
  const STRIDE = 20;                                                           // bytes a vertex: x y z (float), r g b a (byte), nx ny nz (byte) + 1 spare
  let gl = null, cv = null, prog = null, U = {}, lost = false, meshes = [], fogC = [0.6, 0.75, 0.9], sky = null, skyMesh = null;
  let fov = 60, near = 0.5, far = 400, fogNear = 120, fogFar = 380, sunD = [0.4, 0.8, 0.45], amb = 0.45, sunK = 0.6;
  const VP = new Float32Array(16), PV = new Float32Array(16), V = new Float32Array(16), PL = new Float32Array(24), ID = new Float32Array([1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1]);
  const NOADD = new Float32Array(4), eye = new Float32Array(3);

  const VS = 'attribute vec3 aP; attribute vec4 aC; attribute vec3 aN;' +
    'uniform mat4 uVP; uniform mat4 uM; uniform vec3 uL; uniform vec3 uEye; uniform vec4 uK; uniform vec3 uF; uniform vec4 uAdd; uniform float uSky;' +
    'varying vec4 vC;' +
    'void main(){ vec4 w = uM * vec4(aP, 1.0); vec3 n = normalize((uM * vec4(aN, 0.0)).xyz);' +
    ' vec3 c = aC.rgb * (uK.x + uK.y * max(dot(n, uL), 0.0)) + uAdd.rgb;' +             // uK: ambient, sun, fog start, fog end
    ' float f = clamp((distance(w.xyz, uEye) - uK.z) / (uK.w - uK.z), 0.0, 1.0);' +
    ' vC = vec4(mix(mix(c, uF, f), aC.rgb, uSky), 1.0);' +
    ' gl_Position = mix(uVP * w, vec4(aP.xy, 0.9999, 1.0), uSky); }';              // the sky: a quad straight in clip space, at the back
  const FS = 'precision mediump float; varying vec4 vC; void main(){ gl_FragColor = vC; }';

  // ---------------- setup, and again after the TV took the GPU away (webglcontextlost: a low-memory TV does this)
  function setup() {
    const sh = (t, s) => { const o = gl.createShader(t); gl.shaderSource(o, s); gl.compileShader(o); if (!gl.getShaderParameter(o, gl.COMPILE_STATUS)) throw new Error(gl.getShaderInfoLog(o)); return o; };
    prog = gl.createProgram(); gl.attachShader(prog, sh(gl.VERTEX_SHADER, VS)); gl.attachShader(prog, sh(gl.FRAGMENT_SHADER, FS));
    gl.bindAttribLocation(prog, 0, 'aP'); gl.bindAttribLocation(prog, 1, 'aC'); gl.bindAttribLocation(prog, 2, 'aN');
    gl.linkProgram(prog); if (!gl.getProgramParameter(prog, gl.LINK_STATUS)) throw new Error(gl.getProgramInfoLog(prog));
    gl.useProgram(prog);
    for (const n of ['uVP', 'uM', 'uL', 'uEye', 'uK', 'uF', 'uAdd', 'uSky']) U[n] = gl.getUniformLocation(prog, n);
    gl.enableVertexAttribArray(0); gl.enableVertexAttribArray(1); gl.enableVertexAttribArray(2);
    gl.enable(gl.DEPTH_TEST); gl.enable(gl.CULL_FACE); gl.depthFunc(gl.LEQUAL);
    for (const m of meshes) upload(m);
    dyn.buf = gl.createBuffer(); gl.bindBuffer(gl.ARRAY_BUFFER, dyn.buf); gl.bufferData(gl.ARRAY_BUFFER, dyn.data.byteLength, gl.DYNAMIC_DRAW);
    bound = null;
  }
  K3.init = (o) => {
    o = o || {};
    if (o.res) K3.res = o.res; if (o.fov) fov = o.fov; if (o.near) near = o.near; if (o.far) far = o.far;
    if (o.fog) { fogC = rgb(o.fog).map((v) => v / 255); fogNear = o.fogNear || far * 0.3; fogFar = o.fogFar || far * 0.95; }
    if (o.ambient != null) amb = o.ambient; if (o.sun != null) sunK = o.sun;
    if (o.sky) K3.sky(o.sky[0], o.sky[1]);
    cv = document.getElementById('gl');
    if (!cv) { cv = document.createElement('canvas'); cv.id = 'gl'; const c2 = document.getElementById('c'); c2.parentNode.insertBefore(cv, c2); }
    const opt = { alpha: false, antialias: false, depth: true, stencil: false, premultipliedAlpha: false, preserveDrawingBuffer: false, powerPreference: 'high-performance' };
    try { gl = cv.getContext('webgl', opt) || cv.getContext('experimental-webgl', opt); } catch (e) { gl = null; }
    if (!gl) return false;
    cv.addEventListener('webglcontextlost', (e) => { e.preventDefault(); lost = true; }, false);
    cv.addEventListener('webglcontextrestored', () => { lost = false; setup(); }, false);
    try { setup(); } catch (e) { console.log('K3: ' + e.message); gl = null; return false; }
    K3.ok = true; return true;
  };

  // ---------------- colours and numbers
  const cache = {};
  function rgb(c) {
    if (typeof c !== 'string') return c;
    let v = cache[c]; if (v) return v;
    const n = parseInt(c.slice(1), 16); v = cache[c] = [n >> 16, (n >> 8) & 255, n & 255]; return v;
  }
  K3.rgb = rgb;
  K3.m4 = () => new Float32Array(ID);                                         // a matrix to keep and fill each frame with place()
  // a model matrix from position, yaw (about y), pitch (about x), roll (about z) and a uniform scale, written into m: Ry * Rx * Rz * s
  K3.place = (m, x, y, z, yaw, pitch, roll, s) => {
    const cy = Math.cos(yaw || 0), sy = Math.sin(yaw || 0), cp = Math.cos(pitch || 0), sp = Math.sin(pitch || 0), cr = Math.cos(roll || 0), sr = Math.sin(roll || 0), k = s || 1;
    m[0] = (cy * cr + sy * sp * sr) * k; m[1] = cp * sr * k; m[2] = (-sy * cr + cy * sp * sr) * k; m[3] = 0;
    m[4] = (-cy * sr + sy * sp * cr) * k; m[5] = cp * cr * k; m[6] = (sy * sr + cy * sp * cr) * k; m[7] = 0;
    m[8] = sy * cp * k; m[9] = -sp * k; m[10] = cy * cp * k; m[11] = 0;
    m[12] = x; m[13] = y; m[14] = z; m[15] = 1;
    return m;
  };
  function mul(o, a, b) {                                                      // o = a * b (column-major)
    for (let i = 0; i < 4; i++) {
      const b0 = b[i * 4], b1 = b[i * 4 + 1], b2 = b[i * 4 + 2], b3 = b[i * 4 + 3];
      o[i * 4] = a[0] * b0 + a[4] * b1 + a[8] * b2 + a[12] * b3; o[i * 4 + 1] = a[1] * b0 + a[5] * b1 + a[9] * b2 + a[13] * b3;
      o[i * 4 + 2] = a[2] * b0 + a[6] * b1 + a[10] * b2 + a[14] * b3; o[i * 4 + 3] = a[3] * b0 + a[7] * b1 + a[11] * b2 + a[15] * b3;
    }
  }

  // ---------------- meshes: a builder collects flat-shaded triangles, build() packs them into one buffer (kept for a lost context)
  K3.geo = () => {
    const P = [], C = [], N = [], T = new Float32Array(16); let has = false;
    const pt = (x, y, z, o) => {                                               // a point through the builder's current transform
      if (!has) { o[0] = x; o[1] = y; o[2] = z; return o; }
      o[0] = T[0] * x + T[4] * y + T[8] * z + T[12]; o[1] = T[1] * x + T[5] * y + T[9] * z + T[13]; o[2] = T[2] * x + T[6] * y + T[10] * z + T[14]; return o;
    };
    const a = [0, 0, 0], b = [0, 0, 0], c = [0, 0, 0];
    const g = {
      at(x, y, z, yaw, pitch, roll, s) { if (x == null) has = false; else { K3.place(T, x, y, z, yaw, pitch, roll, s); has = true; } return g; },
      tri(p, q, r, col) {                                                     // counter-clockwise seen from outside
        pt(p[0], p[1], p[2], a); pt(q[0], q[1], q[2], b); pt(r[0], r[1], r[2], c);
        const ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2], vx = c[0] - a[0], vy = c[1] - a[1], vz = c[2] - a[2];
        let nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx; const l = Math.hypot(nx, ny, nz) || 1; nx /= l; ny /= l; nz /= l;
        const k = rgb(col);
        for (const v of [a, b, c]) { P.push(v[0], v[1], v[2]); C.push(k[0], k[1], k[2]); N.push(nx, ny, nz); }
        return g;
      },
      quad(p, q, r, s, col) { g.tri(p, q, r, col); return g.tri(p, r, s, col); },
      // flat ground in tiles of about `tile` metres: fog is worked out per vertex, so one huge quad (its corners all far away) came out
      // all fog; col2: a second colour for a checker
      plane(x, y, z, w, d, tile, col, col2) {
        const nx = Math.max(1, Math.round(w / tile)), nz = Math.max(1, Math.round(d / tile)), sx = w / nx, sz = d / nz, x0 = x - w / 2, z0 = z - d / 2;
        for (let i = 0; i < nx; i++) for (let j = 0; j < nz; j++) {
          const a = x0 + i * sx, b = z0 + j * sz;
          g.quad([a, y, b + sz], [a + sx, y, b + sz], [a + sx, y, b], [a, y, b], col2 && (i + j) & 1 ? col2 : col);
        }
        return g;
      },
      box(x, y, z, w, h, d, col, top) {                                         // centred on x, z, standing on y; top: another colour for the lid
        const x0 = x - w / 2, x1 = x + w / 2, y1 = y + h, z0 = z - d / 2, z1 = z + d / 2;
        g.quad([x0, y, z1], [x1, y, z1], [x1, y1, z1], [x0, y1, z1], col); g.quad([x1, y, z0], [x0, y, z0], [x0, y1, z0], [x1, y1, z0], col);
        g.quad([x1, y, z1], [x1, y, z0], [x1, y1, z0], [x1, y1, z1], col); g.quad([x0, y, z0], [x0, y, z1], [x0, y1, z1], [x0, y1, z0], col);
        g.quad([x0, y1, z1], [x1, y1, z1], [x1, y1, z0], [x0, y1, z0], top || col); return g.quad([x0, y, z0], [x1, y, z0], [x1, y, z1], [x0, y, z1], col);
      },
      cyl(x, y, z, r, h, n, col, r2, top) {                                    // upright; r2: the top radius (0 = a cone); n sides
        const rt = r2 == null ? r : r2;
        for (let i = 0; i < n; i++) {
          const t0 = i / n * 6.2832, t1 = (i + 1) / n * 6.2832, c0 = Math.cos(t0), s0 = Math.sin(t0), c1 = Math.cos(t1), s1 = Math.sin(t1);
          const A = [x + c0 * r, y, z + s0 * r], B = [x + c1 * r, y, z + s1 * r], Ct = [x + c1 * rt, y + h, z + s1 * rt], D = [x + c0 * rt, y + h, z + s0 * rt];
          if (rt > 0) g.quad(B, A, D, Ct, col); else g.tri(B, A, D, col);
          if (rt > 0) g.tri(D, [x, y + h, z], Ct, top || col);
          g.tri(A, B, [x, y, z], col);
        }
        return g;
      },
      wheel(x, y, z, r, w, n, col, hub) {                                      // a cylinder lying along x (a wheel, a log)
        for (let i = 0; i < n; i++) {
          const t0 = i / n * 6.2832, t1 = (i + 1) / n * 6.2832, y0 = Math.cos(t0) * r, z0 = Math.sin(t0) * r, y1 = Math.cos(t1) * r, z1 = Math.sin(t1) * r, h = w / 2;
          g.quad([x - h, y + y0, z + z0], [x - h, y + y1, z + z1], [x + h, y + y1, z + z1], [x + h, y + y0, z + z0], col);
          g.tri([x + h, y, z], [x + h, y + y0, z + z0], [x + h, y + y1, z + z1], hub || col);
          g.tri([x - h, y, z], [x - h, y + y1, z + z1], [x - h, y + y0, z + z0], hub || col);
        }
        return g;
      },
      ball(x, y, z, r, col, seg) {                                              // a low-poly sphere centred on x y z
        const n = seg || 6, m = Math.max(3, n >> 1);
        for (let j = 0; j < m; j++) {
          const p0 = j / m * Math.PI, p1 = (j + 1) / m * Math.PI, y0 = Math.cos(p0) * r, y1 = Math.cos(p1) * r, q0 = Math.sin(p0) * r, q1 = Math.sin(p1) * r;
          for (let i = 0; i < n; i++) {
            const t0 = i / n * 6.2832, t1 = (i + 1) / n * 6.2832;
            const A = [x + Math.cos(t0) * q0, y + y0, z + Math.sin(t0) * q0], B = [x + Math.cos(t1) * q0, y + y0, z + Math.sin(t1) * q0];
            const Cc = [x + Math.cos(t1) * q1, y + y1, z + Math.sin(t1) * q1], D = [x + Math.cos(t0) * q1, y + y1, z + Math.sin(t0) * q1];
            if (j > 0) g.tri(A, B, Cc, col); if (j < m - 1) g.tri(A, Cc, D, col);
          }
        }
        return g;
      },
      count() { return P.length / 9; },
      build() {
        const n = P.length / 3, buf = new ArrayBuffer(n * STRIDE), f = new Float32Array(buf), u = new Uint8Array(buf), s = new Int8Array(buf);
        let lo = [1e9, 1e9, 1e9], hi = [-1e9, -1e9, -1e9];
        for (let i = 0; i < n; i++) {
          const o = i * 5, ob = i * STRIDE;
          for (let k = 0; k < 3; k++) { const v = P[i * 3 + k]; f[o + k] = v; if (v < lo[k]) lo[k] = v; if (v > hi[k]) hi[k] = v; }
          u[ob + 12] = C[i * 3]; u[ob + 13] = C[i * 3 + 1]; u[ob + 14] = C[i * 3 + 2]; u[ob + 15] = 255;
          s[ob + 16] = Math.round(N[i * 3] * 127); s[ob + 17] = Math.round(N[i * 3 + 1] * 127); s[ob + 18] = Math.round(N[i * 3 + 2] * 127);
        }
        const cx = (lo[0] + hi[0]) / 2, cy = (lo[1] + hi[1]) / 2, cz = (lo[2] + hi[2]) / 2;
        const mesh = { data: buf, n, buf: null, b: [cx, cy, cz, n ? Math.hypot(hi[0] - cx, hi[1] - cy, hi[2] - cz) : 0] };
        meshes.push(mesh); if (gl) upload(mesh); return mesh;
      },
    };
    return g;
  };
  function upload(m) { m.buf = gl.createBuffer(); gl.bindBuffer(gl.ARRAY_BUFFER, m.buf); gl.bufferData(gl.ARRAY_BUFFER, m.data, gl.STATIC_DRAW); }
  K3.free = (m) => { const i = meshes.indexOf(m); if (i >= 0) meshes.splice(i, 1); if (gl && m.buf) gl.deleteBuffer(m.buf); m.buf = null; };
  let bound = null;
  function bind(buf) {
    if (bound === buf) return; bound = buf;
    gl.bindBuffer(gl.ARRAY_BUFFER, buf);
    gl.vertexAttribPointer(0, 3, gl.FLOAT, false, STRIDE, 0); gl.vertexAttribPointer(1, 4, gl.UNSIGNED_BYTE, true, STRIDE, 12); gl.vertexAttribPointer(2, 3, gl.BYTE, true, STRIDE, 16);
  }

  // ---------------- the sky: two colours, top and horizon, in a quad drawn first
  K3.sky = (top, bottom) => {
    sky = [top, bottom]; if (skyMesh) K3.free(skyMesh);
    const g = K3.geo(), t = rgb(top), h = rgb(bottom), mid = [(t[0] + h[0]) / 2, (t[1] + h[1]) / 2, (t[2] + h[2]) / 2];
    g.quad([-1, -1, 0], [1, -1, 0], [1, 0.2, 0], [-1, 0.2, 0], h); g.quad([-1, 0.2, 0], [1, 0.2, 0], [1, 0.6, 0], [-1, 0.6, 0], h);
    skyMesh = g.build();
    const f = new Uint8Array(skyMesh.data);                                    // the top edge in the top colour: the quads blend down to the horizon
    for (let i = 0; i < skyMesh.n; i++) { const y = new Float32Array(skyMesh.data, i * STRIDE + 4, 1)[0], c = y > 0.5 ? t : y > 0.1 ? mid : h; f[i * STRIDE + 12] = c[0]; f[i * STRIDE + 13] = c[1]; f[i * STRIDE + 14] = c[2]; }
    for (let i = 0; i < skyMesh.n; i++) { const fl = new Float32Array(skyMesh.data, i * STRIDE, 3); if (fl[1] > 0.5) fl[1] = 1; }
    if (gl) { gl.deleteBuffer(skyMesh.buf); upload(skyMesh); }
  };

  // ---------------- a frame
  K3.begin = (cam) => {                                                        // cam: { x, y, z, tx, ty, tz, fov? }
    const st = K3.stats; st.draws = st.tris = st.culled = 0;
    if (!gl || lost) return false;
    const Ks = window.Kit && window.Kit.scale ? Math.min(1, window.Kit.scale) : 1, w = Math.round(1280 * K3.res * Ks), h = Math.round(720 * K3.res * Ks);
    if (cv.width !== w || cv.height !== h) { cv.width = w; cv.height = h; }
    st.w = w; st.h = h;
    gl.viewport(0, 0, w, h);
    gl.clearColor(fogC[0], fogC[1], fogC[2], 1); gl.clear(gl.COLOR_BUFFER_BIT | gl.DEPTH_BUFFER_BIT);
    // view: look from (x y z) at (tx ty tz), y up
    let fx = cam.tx - cam.x, fy = cam.ty - cam.y, fz = cam.tz - cam.z; let l = Math.hypot(fx, fy, fz) || 1; fx /= l; fy /= l; fz /= l;
    let sx = fy * 0 - fz * 1, sy = fz * 0 - fx * 0, sz = fx * 1 - fy * 0; l = Math.hypot(sx, sy, sz) || 1; sx /= l; sy /= l; sz /= l;   // side = f x up
    const ux = sy * fz - sz * fy, uy = sz * fx - sx * fz, uz = sx * fy - sy * fx;
    V[0] = sx; V[1] = ux; V[2] = -fx; V[3] = 0; V[4] = sy; V[5] = uy; V[6] = -fy; V[7] = 0; V[8] = sz; V[9] = uz; V[10] = -fz; V[11] = 0;
    V[12] = -(sx * cam.x + sy * cam.y + sz * cam.z); V[13] = -(ux * cam.x + uy * cam.y + uz * cam.z); V[14] = fx * cam.x + fy * cam.y + fz * cam.z; V[15] = 1;
    const f = 1 / Math.tan((cam.fov || fov) * Math.PI / 360), nf = 1 / (near - far);
    PV.fill(0); PV[0] = f / (1280 / 720); PV[5] = f; PV[10] = (far + near) * nf; PV[11] = -1; PV[14] = 2 * far * near * nf;
    mul(VP, PV, V);
    for (let i = 0; i < 6; i++) {                                              // the 6 planes of what the camera sees, for culling
      const s = i & 1 ? -1 : 1, r = i >> 1;
      let a = VP[3] + s * VP[r], b = VP[7] + s * VP[4 + r], c = VP[11] + s * VP[8 + r], d = VP[15] + s * VP[12 + r]; const k = Math.hypot(a, b, c) || 1;
      PL[i * 4] = a / k; PL[i * 4 + 1] = b / k; PL[i * 4 + 2] = c / k; PL[i * 4 + 3] = d / k;
    }
    eye[0] = cam.x; eye[1] = cam.y; eye[2] = cam.z;
    const sl = Math.hypot(sunD[0], sunD[1], sunD[2]);
    gl.uniformMatrix4fv(U.uVP, false, VP); gl.uniform3f(U.uL, sunD[0] / sl, sunD[1] / sl, sunD[2] / sl); gl.uniform3fv(U.uEye, eye);
    gl.uniform4f(U.uK, amb, sunK, fogNear, fogFar); gl.uniform3f(U.uF, fogC[0], fogC[1], fogC[2]); gl.uniform4fv(U.uAdd, NOADD);
    if (skyMesh) {
      gl.depthMask(false); gl.uniform1f(U.uSky, 1); gl.uniformMatrix4fv(U.uM, false, ID); bind(skyMesh.buf); gl.drawArrays(gl.TRIANGLES, 0, skyMesh.n); gl.depthMask(true);
      st.draws++;
    }
    gl.uniform1f(U.uSky, 0); lastAdd = null; lastM = null;
    dyn.n = 0;
    return true;
  };
  let lastAdd = null, lastM = null;
  K3.visible = (x, y, z, r) => {
    for (let i = 0; i < 24; i += 4) if (PL[i] * x + PL[i + 1] * y + PL[i + 2] * z + PL[i + 3] < -r) return false;
    return true;
  };
  // one mesh; m: its matrix (K3.place), null for a mesh built in world space; add: [r, g, b] 0..1 added to its colour (a flash)
  K3.draw = (mesh, m, add) => {
    if (!gl || lost || !mesh.n) return;
    const b = mesh.b; let x = b[0], y = b[1], z = b[2], r = b[3];
    if (m) { x = m[0] * b[0] + m[4] * b[1] + m[8] * b[2] + m[12]; y = m[1] * b[0] + m[5] * b[1] + m[9] * b[2] + m[13]; z = m[2] * b[0] + m[6] * b[1] + m[10] * b[2] + m[14]; r *= Math.hypot(m[0], m[1], m[2]); }
    if (!K3.visible(x, y, z, r)) { K3.stats.culled++; return; }
    const mm = m || ID; if (mm !== lastM || m) { gl.uniformMatrix4fv(U.uM, false, mm); lastM = mm; }
    if (add !== lastAdd) { if (add) gl.uniform4f(U.uAdd, add[0], add[1], add[2], 0); else gl.uniform4fv(U.uAdd, NOADD); lastAdd = add; }
    bind(mesh.buf); gl.drawArrays(gl.TRIANGLES, 0, mesh.n);
    K3.stats.draws++; K3.stats.tris += mesh.n / 3;
  };

  // ---------------- small things that change every frame (sparks, dust, item boxes): cubes into one stream, drawn in one go at end()
  const DYN = 6144, dyn = { data: new ArrayBuffer(DYN * STRIDE), n: 0, buf: null };
  dyn.f = new Float32Array(dyn.data); dyn.u = new Uint8Array(dyn.data); dyn.s = new Int8Array(dyn.data);
  const CUBE = [[0, 0, 1], [0, 0, -1], [1, 0, 0], [-1, 0, 0], [0, 1, 0], [0, -1, 0]], CORN = [-1, -1, 1, -1, 1, 1, -1, 1], FP = new Float32Array(12);
  const ORD_A = [0, 1, 2, 0, 2, 3], ORD_B = [0, 2, 1, 0, 3, 2];
  function vtx(x, y, z, c, nx, ny, nz) {
    const i = dyn.n++, o = i * 5, ob = i * STRIDE;
    dyn.f[o] = x; dyn.f[o + 1] = y; dyn.f[o + 2] = z; dyn.u[ob + 12] = c[0]; dyn.u[ob + 13] = c[1]; dyn.u[ob + 14] = c[2]; dyn.u[ob + 15] = 255;
    dyn.s[ob + 16] = nx * 127; dyn.s[ob + 17] = ny * 127; dyn.s[ob + 18] = nz * 127;
  }
  K3.cube = (x, y, z, s, col, spin) => {                                       // a cube of side s centred on x y z, turned spin about y
    if (dyn.n + 36 > DYN) flush();
    if (!K3.visible(x, y, z, s)) return;
    const c = rgb(col), h = s / 2, co = Math.cos(spin || 0), si = Math.sin(spin || 0), cs = co * h, sn = si * h, P = FP;
    for (let f = 0; f < 6; f++) {
      // the face's 4 corners: along its normal by h, spread by h on the two other axes, then turned (no arrays made: this runs per frame)
      const n = CUBE[f], ax = n[0], ay = n[1], az = n[2], ux = ay ? 1 : 0, uy = ay ? 0 : 1, vx = ay ? 0 : az, vz = ay ? 1 : -ax;
      for (let k = 0; k < 4; k++) {
        const k0 = CORN[k * 2], k1 = CORN[k * 2 + 1], lx = ax + ux * k0 + vx * k1, ly = ay + uy * k0, lz = az + vz * k1;
        P[k * 3] = x + lx * cs + lz * sn; P[k * 3 + 1] = y + ly * h; P[k * 3 + 2] = z - lx * sn + lz * cs;
      }
      const nx = ax * co + az * si, nz = -ax * si + az * co, order = (ay ? ay : 1) * ((ax || az) ? (ax ? ax : az) : 1) > 0 ? ORD_A : ORD_B;
      for (let i = 0; i < 6; i++) { const q = order[i]; vtx(P[q * 3], P[q * 3 + 1], P[q * 3 + 2], c, nx, ay, nz); }
    }
  };
  function flush() {
    if (!dyn.n || !gl || lost) { dyn.n = 0; return; }
    gl.disable(gl.CULL_FACE);                                                 // a cube's faces wind either way here: no culling, they are tiny
    gl.bindBuffer(gl.ARRAY_BUFFER, dyn.buf); bound = null; gl.bufferSubData(gl.ARRAY_BUFFER, 0, new Uint8Array(dyn.data, 0, dyn.n * STRIDE));
    bind(dyn.buf); gl.uniformMatrix4fv(U.uM, false, ID); lastM = ID; if (lastAdd) { gl.uniform4fv(U.uAdd, NOADD); lastAdd = null; }
    gl.drawArrays(gl.TRIANGLES, 0, dyn.n); gl.enable(gl.CULL_FACE);
    K3.stats.draws++; K3.stats.tris += dyn.n / 3; dyn.n = 0;
  }
  K3.end = () => flush();

  // a world point on Kit's 1280x720 canvas: out = [x, y, in front of the camera]
  K3.toScreen = (x, y, z, out) => {
    const cx = VP[0] * x + VP[4] * y + VP[8] * z + VP[12], cy = VP[1] * x + VP[5] * y + VP[9] * z + VP[13], w = VP[3] * x + VP[7] * y + VP[11] * z + VP[15];
    out[0] = (cx / w * 0.5 + 0.5) * 1280; out[1] = (0.5 - cy / w * 0.5) * 720; out[2] = w > 0 ? 1 : 0; return out;
  };
})();
