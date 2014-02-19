package com.turikhay.tlauncher.minecraft;

import java.util.regex.Pattern;

public class CrashSignature {
	public final Pattern pattern;
	public final int exitcode;
	public final String name, path;
	
	CrashSignature(int exitcode, String pattern, String name, String path){
		this.pattern = (pattern != null)? Pattern.compile(pattern) : null;
		this.exitcode = exitcode;
		this.name = name;
		this.path = path;
	}
	
	public boolean match(String line){
		if(pattern == null) return false;
		return pattern.matcher(line).matches();
	}
	
	public boolean match(int exit){
		if(exitcode == 0) return false;
		return exit == exitcode;
	}
}

/*package com.turikhay.tlauncher.minecraft;

import java.util.regex.Pattern;

public class CrashSignature {
	private String name, path, pattern, version;
	private boolean forge, fake;
	private int exit;
	
	private Pattern compiledPattern;
	
	public String getName(){
		return name;
	}
	
	public String getPath(){
		return path;
	}
	
	public String getPattern(){
		return pattern;
	}
	
	public String getVersion(){
		return version;
	}
	
	public void setPattern(String pattern){
		this.pattern = pattern;
		this.compiledPattern = Pattern.compile(pattern);
	}
	
	public int getExitCode(){
		return exit;
	}
	
	public boolean isFake(){
		return fake;
	}
	
	public boolean isForge(){
		return forge;
	}
	
	public boolean match(String line){
		if(pattern == null) return false;
		return compiledPattern.matcher(line).matches();
	}
	
	public boolean match(int exit){
		if(exit == 0) return false;
		return this.exit == exit;
	}
	
	public String toString(){
		return "CrashSignature{name='"+name+"', path='"+path+"', pattern='"+pattern+"', exit="+exit+", fake="+fake+", forge="+forge+"}";
	}
}
*/