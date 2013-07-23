/*
 * Copyright 2013 Yusef Badri - All rights reserved.
 * NAF is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3).
 */
package com.grey.naf.nafman;

import com.grey.base.utils.DynLoader;
import com.grey.base.utils.FileOps;

final class ResourceManager
{
	private final java.util.HashMap<String, Resource> resources = new java.util.HashMap<String, Resource>();
	private final Primary primary;
	private final HTTP http;
	private final long cachettl;

	ResourceManager(com.grey.base.config.XmlConfig cfg, Primary p, HTTP h, long cachetime)
		throws com.grey.base.ConfigException, java.io.IOException, java.net.URISyntaxException,
			javax.xml.transform.TransformerConfigurationException
	{
		primary = p;
		http = h;
		cachettl = cachetime;

		javax.xml.transform.TransformerFactory fact = javax.xml.transform.TransformerFactory.newInstance();
		java.util.Set<String> rsrc_names = Registry.get().getResourceNames();
		java.util.Iterator<String> it = rsrc_names.iterator();
		while (it.hasNext()) {
			String name = it.next();
			Resource rsrc = loadResource(name, cfg, fact);
			resources.put(name, rsrc);
		}
	}

	public java.nio.ByteBuffer getContent(String rsrc_name) throws com.grey.base.FaultException, java.io.IOException
	{
		Resource rsrc = getResource(rsrc_name);
		if (rsrc == null) return null;
		return rsrc.getContent(cachettl, primary);
	}

	public byte[] formatData(String rsrc_name, byte[] data, com.grey.base.utils.HashedMap<String, String> params) throws com.grey.base.FaultException
	{
		Resource rsrc = getResource(rsrc_name);
		if (rsrc == null) return null;
		return rsrc.formatData(data, params);
	}

	private Resource getResource(String rsrc_name)
	{
		if (rsrc_name.length() == 0) rsrc_name = Registry.get().getHomePage();
		return resources.get(rsrc_name);
	}

	private Resource loadResource(String name, com.grey.base.config.XmlConfig cfg, javax.xml.transform.TransformerFactory fact)
		throws com.grey.base.ConfigException, java.io.IOException, java.net.URISyntaxException,
			javax.xml.transform.TransformerConfigurationException
	{
		Registry.DefResource def = Registry.get().getResource(name);
		if (def == null) return null;
		String cfgkey = name.replace(':', '_').replace('.', '_').replace('-', '_');
		String path = cfg.getValue(cfgkey, false, def.path);
		if (path == null) {
			if (def.gen != null) return new Resource(def, http, null, null);
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
		byte[] filedata = (strm == null ? null : FileOps.read(strm, -1, null));
		if (filedata == null) return null;
		int pos = path.lastIndexOf('.');
		String sfx = (pos == -1 ? null : path.substring(pos+1));

		if (sfx != null && sfx.equalsIgnoreCase("xsl")) {
			java.io.ByteArrayInputStream bstrm = new java.io.ByteArrayInputStream(filedata);
			javax.xml.transform.stream.StreamSource src = new javax.xml.transform.stream.StreamSource(bstrm);
			javax.xml.transform.Transformer xslt = fact.newTransformer(src);
			return new Resource(def, http, xslt, null);
		}
		java.nio.ByteBuffer niobuf = http.buildStaticResponse(filedata, def.mimetype);
		return new Resource(def, http, null, niobuf);
	}
}