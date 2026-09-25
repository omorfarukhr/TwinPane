package com.twinpane.app

data class Template(val name: String, val html: String, val css: String, val js: String)

object Templates {

    val default = Template(
        name = "Hello TwinPane",
        html = """
            <h1>Hello TwinPane 👋</h1>
            <p>Edit the code above and watch it update live.</p>
            <button onclick="hi()">Click me</button>
        """.trimIndent(),
        css = """
            body { font-family: sans-serif; padding: 16px; }
            h1 { color: #7c5cff; }
            button { padding: 10px 20px; border: none; border-radius: 8px;
                     background: #7c5cff; color: #fff; font-size: 16px; }
        """.trimIndent(),
        js = """
            function hi() {
              console.log('Clicked!');
              alert('Hi from TwinPane!');
            }
        """.trimIndent()
    )

    val all = listOf(
        default,
        Template("Blank", "", "", ""),
        Template(
            name = "Counter",
            html = """
                <div class="app">
                  <h1 id="count">0</h1>
                  <div class="row">
                    <button id="dec">-</button>
                    <button id="inc">+</button>
                  </div>
                </div>
            """.trimIndent(),
            css = """
                body { margin: 0; height: 100vh; display: grid; place-items: center;
                       background: #0f1115; color: #fff; font-family: sans-serif; }
                h1 { font-size: 64px; margin: 0 0 16px; text-align: center; }
                .row { display: flex; gap: 12px; }
                button { font-size: 28px; width: 64px; height: 64px; border: none;
                         border-radius: 16px; background: #7c5cff; color: #fff; }
            """.trimIndent(),
            js = """
                let count = 0;
                const el = document.getElementById('count');
                document.getElementById('inc').onclick = () => { el.textContent = ++count; };
                document.getElementById('dec').onclick = () => { el.textContent = --count; };
                console.log('Counter ready');
            """.trimIndent()
        ),
        Template(
            name = "Profile Card",
            html = """
                <div class="card">
                  <div class="avatar">JD</div>
                  <h2>Jane Doe</h2>
                  <p>Frontend Developer · New York</p>
                  <button id="follow">Follow</button>
                </div>
            """.trimIndent(),
            css = """
                body { margin: 0; min-height: 100vh; display: grid; place-items: center;
                       background: linear-gradient(135deg, #7c5cff, #00d1b2); font-family: sans-serif; }
                .card { background: #fff; padding: 24px; border-radius: 20px; text-align: center;
                        width: 260px; box-shadow: 0 10px 30px rgba(0,0,0,.2); }
                .avatar { width: 72px; height: 72px; margin: 0 auto 12px; border-radius: 50%;
                          background: #7c5cff; color: #fff; display: grid; place-items: center;
                          font-size: 24px; font-weight: bold; }
                p { color: #666; }
                button { padding: 10px 24px; border: none; border-radius: 999px;
                         background: #111; color: #fff; font-size: 16px; }
            """.trimIndent(),
            js = """
                const btn = document.getElementById('follow');
                let following = false;
                btn.onclick = () => {
                  following = !following;
                  btn.textContent = following ? 'Following ✓' : 'Follow';
                  console.log('following:', following);
                };
            """.trimIndent()
        ),
        Template(
            name = "CSS Animation",
            html = """<div class="ball"></div>""",
            css = """
                body { margin: 0; height: 100vh; display: grid; place-items: center; background: #0f1115; }
                .ball { width: 60px; height: 60px; border-radius: 50%; background: #00d1b2;
                        animation: bounce 1s ease-in-out infinite alternate; }
                @keyframes bounce {
                  from { transform: translateY(-80px); }
                  to   { transform: translateY(80px); background: #7c5cff; }
                }
            """.trimIndent(),
            js = """console.log('Animation running');"""
        )
    )
}
