package com.turikhay.tlauncher.ui.loc;

import com.turikhay.tlauncher.ui.center.CenterPanel;
import com.turikhay.tlauncher.ui.text.ExtendedTextField;

public abstract class LocalizableTextField extends ExtendedTextField implements LocalizableComponent {
	private static final long serialVersionUID = 359096767189321072L;
	
	private String placeholderPath;

	public LocalizableTextField(CenterPanel panel, String placeholderPath, String value) {
		super(panel, null, value);
		
		this.setValue(value);
		this.setPlaceholder(placeholderPath);
	}
	
	public LocalizableTextField(CenterPanel panel, String placeholderPath) {
		this(panel, placeholderPath, null);
	}
	
	public LocalizableTextField(String placeholderPath) {
		this(null, placeholderPath, null);
	}
	
	public LocalizableTextField() {
		this(null, null, null);
	}
	
	public void setPlaceholder(String placeholderPath){
		this.placeholderPath = placeholderPath;
		super.setPlaceholder((Localizable.get() == null)? placeholderPath : Localizable.get().get(placeholderPath));
	}
	
	public String getPlaceholderPath(){
		return this.placeholderPath;
	}
	
	public void updateLocale(){
		this.setPlaceholder(placeholderPath);
	}
}
