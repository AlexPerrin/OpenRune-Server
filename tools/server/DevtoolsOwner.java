// The owner check of a test desktop's devtools endpoint (tools/track.sh desktop_start compiles it into the template once per
// source into a jar and names it in every JVM of the instance through JAVA_TOOL_OPTIONS, as -javaagent: the one option that puts
// a class on the system class path the JDK loads a provider from, which the client's own -cp and a boot class path cannot).
//
// The JDK's HttpServer provider, -Dcom.sun.net.httpserver.HttpServerProvider=DevtoolsOwner: its servers are the JDK's own,
// each context filtered so that a request to the devtools port of -Dosrstest.devtools=<port>:<token> is answered only when
// it carries "Authorization: Bearer <token>", the token of the up that started the instance (its state/<slug>/up.json);
// any other request to that port gets 401. The devtools plugin binds 127.0.0.1, which every local process reaches: the
// token is what makes the endpoint answer its owner alone. Requests to any other port pass unchanged.

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsServer;
import com.sun.net.httpserver.spi.HttpServerProvider;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.Executor;

public final class DevtoolsOwner extends HttpServerProvider
{
	static final String[] OWNER = System.getProperty("osrstest.devtools", "").split(":", 2);

	public static void premain(String args)
	{
	}

	final HttpServerProvider jdk = new sun.net.httpserver.DefaultHttpServerProvider();

	@Override
	public HttpServer createHttpServer(InetSocketAddress addr, int backlog) throws IOException
	{
		return new Owned(jdk.createHttpServer(addr, backlog));
	}

	@Override
	public HttpsServer createHttpsServer(InetSocketAddress addr, int backlog) throws IOException
	{
		return jdk.createHttpsServer(addr, backlog);
	}

	static final Filter GATE = new Filter()
	{
		@Override
		public void doFilter(HttpExchange x, Chain chain) throws IOException
		{
			String auth = String.valueOf(x.getRequestHeaders().getFirst("Authorization"));
			if (OWNER.length < 2 || x.getLocalAddress().getPort() != Integer.parseInt(OWNER[0])
				|| MessageDigest.isEqual(("Bearer " + OWNER[1]).getBytes(StandardCharsets.UTF_8), auth.getBytes(StandardCharsets.UTF_8)))
			{
				chain.doFilter(x);
				return;
			}
			x.getRequestBody().readAllBytes();
			byte[] b = ("{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":{\"code\":-32001,\"message\":\"this desktop answers only the up that "
				+ "started it: send Authorization: Bearer <token>, the token of your change's state/<slug>/up.json\"}}").getBytes(StandardCharsets.UTF_8);
			x.getResponseHeaders().set("Content-Type", "application/json");
			x.sendResponseHeaders(401, b.length);
			x.getResponseBody().write(b);
			x.close();
		}

		@Override
		public String description()
		{
			return "devtools owner";
		}
	};

	static final class Owned extends HttpServer
	{
		final HttpServer s;

		Owned(HttpServer s)
		{
			this.s = s;
		}

		static HttpContext gated(HttpContext c)
		{
			c.getFilters().add(GATE);
			return c;
		}

		@Override public void bind(InetSocketAddress addr, int backlog) throws IOException { s.bind(addr, backlog); }
		@Override public void start() { s.start(); }
		@Override public void setExecutor(Executor e) { s.setExecutor(e); }
		@Override public Executor getExecutor() { return s.getExecutor(); }
		@Override public void stop(int delay) { s.stop(delay); }
		@Override public HttpContext createContext(String path, HttpHandler h) { return gated(s.createContext(path, h)); }
		@Override public HttpContext createContext(String path) { return gated(s.createContext(path)); }
		@Override public void removeContext(String path) { s.removeContext(path); }
		@Override public void removeContext(HttpContext c) { s.removeContext(c); }
		@Override public InetSocketAddress getAddress() { return s.getAddress(); }
	}
}
