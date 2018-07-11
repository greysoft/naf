/*
 * Copyright 2013-2018 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.nafman;

import com.grey.base.utils.DynLoader;
import com.grey.base.utils.FileOps;
import com.grey.base.utils.PkgInfo;

class ResourceManager
{
	private static final String SUBTOKEN = "_SUBTOKEN_";
	private static final String TOKEN_NAFVER = "NAFVER";

	private final java.util.HashMap<String, NafManResource> resources = new java.util.HashMap<String, NafManResource>();
	private final PrimaryAgent primary;
	private final HTTP http;
	private final long cachettl;
	private final String nafver;

	ResourceManager(com.grey.base.config.XmlConfig cfg, PrimaryAgent p, HTTP h, long cachetime)
		throws java.io.IOException, javax.xml.transform.TransformerConfigurationException
	{
		primary = p;
		http = h;
		cachettl = cachetime;

		CharSequence ver = PkgInfo.getVersion(getClass(), null);
		nafver = (ver == null ? "Unknown" : ver.toString());

		javax.xml.transform.TransformerFactory fact = javax.xml.transform.TransformerFactory.newInstance();
		java.util.Set<String> rsrc_names = primary.getRegistry().getResourceNames();
		java.util.Iterator<String> it = rsrc_names.iterator();
		while (it.hasNext()) {
			String name = it.next();
			NafManResource rsrc = loadResource(name, cfg, fact);
			resources.put(name, rsrc);
		}
	}

	public java.nio.ByteBuffer getContent(String rsrc_name) throws java.io.IOException
	{
		NafManResource rsrc = getResource(rsrc_name);
		if (rsrc == null) return null;
		return rsrc.getContent(cachettl, primary);
	}

	public byte[] formatData(String rsrc_name, byte[] data, com.grey.base.collections.HashedMap<String, String> params)
	{
		NafManResource rsrc = getResource(rsrc_name);
		if (rsrc == null) return null;
		return rsrc.formatData(data, params);
	}

	private NafManResource getResource(String rsrc_name)
	{
		if (rsrc_name.length() == 0) rsrc_name = primary.getRegistry().getHomePage();
		return resources.get(rsrc_name);
	}

	private NafManResource loadResource(String name, com.grey.base.config.XmlConfig cfg, javax.xml.transform.TransformerFactory fact)
		throws java.io.IOException, javax.xml.transform.TransformerConfigurationException
	{
		NafManRegistry.DefResource def = primary.getRegistry().getResource(name);
		if (def == null) return null;
		String cfgkey = name.replace(':', '_').replace('.', '_').replace('-', '_');
		String path = cfg.getValue(cfgkey, false, def.path);
		if (path == null) {
			if (def.gen != null) return new NafManResource(def, http, null, null, null);
			return null;
		}
		String filepfx = "file:";
		java.io.InputStream strm = null;
		if (path.startsWith(filepfx)) {
			java.io.File fh = new java.io.File(path.substring(filepfx.length()));
			strm = new java.io.FileInputStream(fh);
		} else {
			java.net.URL url = DynLoader.getResource(path, getClass());
			if (url != null) strm = url.openStream();
		}
		byte[] filedata = (strm == null ? null : FileOps.read(strm, -1, null).toArray(true));
		if (filedata == null) return null;

		String strdata = new String(filedata);
		strdata = replaceToken(TOKEN_NAFVER, nafver, strdata);
		filedata = strdata.getBytes();

		int pos = path.lastIndexOf('.');
		String sfx = (pos == -1 ? null : path.substring(pos+1));

		if (sfx != null && sfx.equalsIgnoreCase("xsl")) {
			return new NafManResource(def, http, null, fact, filedata);
		}
		java.nio.ByteBuffer niobuf = http.buildStaticResponse(filedata, def.mimetype);
		return new NafManResource(def, http, niobuf, null, null);
	}

	private static String replaceToken(String token, String val, String txt)
	{
		return txt.replace(SUBTOKEN+token+"_", val);
	}
}