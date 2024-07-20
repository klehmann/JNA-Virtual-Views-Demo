package com.mindoo.virtualviews;


import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.ibm.commons.util.io.json.JsonJavaObject;

import lotus.domino.Database;
import lotus.domino.Session;

/**
 * REST API base class that restricts access to a list of internal IP addresses
 */
public abstract class InternalBaseHandler {
	private static final List<String> ALLOWED_IPS = Arrays.asList(
			"*"
			);

	public final void service(Session session, Database db, Session sessionAsSigner, HttpServletRequest req, HttpServletResponse response) {
		String ip = req.getRemoteAddr();
		
		if (!ALLOWED_IPS.contains("*") && !ALLOWED_IPS.contains(ip)) {			
			response.setStatus(HttpServletResponse.SC_FORBIDDEN);
			PrintWriter writer;
			try {
				writer = response.getWriter();
				writer.println("Access only allowed internally. Your IP: "+req.getRemoteAddr());
			} catch (IOException e) {
				e.printStackTrace();
			}
			return;
		}
		
		try {
			serviceSecured(session, db, sessionAsSigner, req, response);
		} catch (Throwable e) {
			PrintWriter writer = null;
			try {
				writer = response.getWriter();
			} catch (IOException e1) {
				try {
					ServletOutputStream out = response.getOutputStream();
					writer = new PrintWriter(new OutputStreamWriter(out));
				} catch (IOException e2) {
					e2.printStackTrace();
				}
			}

			response.setContentType("application/json; charset=UTF-8");
			response.setHeader("Cache-Control", "no-cache");
			response.setCharacterEncoding("UTF-8");
			response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			
			e.printStackTrace();
			
			if (writer != null) {
				JsonJavaObject json = new JsonJavaObject();
				json.putBoolean("success", false);
				json.putString("message", e.getMessage());
				writer.println(json);
				writer.flush();
			}
		}
	}

	public abstract void serviceSecured(Session session, Database db, Session sessionAsSigner,
			HttpServletRequest req, HttpServletResponse response) throws Exception;
}