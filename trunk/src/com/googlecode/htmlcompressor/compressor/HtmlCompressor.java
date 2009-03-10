package com.googlecode.htmlcompressor.compressor;

/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.yahoo.platform.yui.compressor.CssCompressor;
import com.yahoo.platform.yui.compressor.JavaScriptCompressor;

/**
 * Class that compresses given HTML source by removing comments, extra spaces and 
 * line breaks while preserving content within &lt;pre>, &lt;textarea>, &lt;script> 
 * and &lt;style> tags. Can optionally compress content inside &lt;script> 
 * or &lt;style> tags using 
 * <a href="http://developer.yahoo.com/yui/compressor/">Yahoo YUI Compressor</a> 
 * library.
 * 
 * @author <a href="mailto:serg472@gmail.com">Sergiy Kovalchuk</a>
 */
public class HtmlCompressor implements Compressor {
	
	private boolean compressJavaScript = false;
	private boolean compressCss = false;
	
	//YUICompressor settings
	private boolean yuiJsNoMunge = false;
	private boolean yuiJsPreserveAllSemiColons = false;
	private boolean yuiJsDisableOptimizations = false;
	private int yuiJsLineBreak = -1;
	private int yuiCssLineBreak = -1;
	
	private String tempPrefix = "%%%COMPRESS~";
	private String tempSuffix = "%%%";
	
	/**
	 * The main method that compresses given HTML source and returns compressed result.
	 * 
	 * @param html HTML content to compress
	 * @return compressed content.
	 * @throws Exception
	 */
	@Override
	public String compress(String html) throws Exception {
		if(html == null || html.length() == 0) {
			return html;
		}
		
		List<String> preBlocks = new ArrayList<String>();
		List<String> taBlocks = new ArrayList<String>();
		List<String> scriptBlocks = new ArrayList<String>();
		List<String> styleBlocks = new ArrayList<String>();
		
		String result = html;
		
		//preserve PRE tags
		String preRule = "<pre[^>]*?>.*?</pre>";
		Pattern prePattern = Pattern.compile(preRule, Pattern.DOTALL | Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
		
		Matcher preMatcher = prePattern.matcher(result);
		while(preMatcher.find()) {
			preBlocks.add(preMatcher.group(0));
		}
		
		result = preMatcher.replaceAll(tempPrefix + "PRE" + tempSuffix);
		
		//preserve TEXTAREA tags
		String taRule = "<textarea[^>]*?>.*?</textarea>";
		Pattern taPattern = Pattern.compile(taRule, Pattern.DOTALL | Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
		
		Matcher taMatcher = taPattern.matcher(result);
		while(taMatcher.find()) {
			taBlocks.add(taMatcher.group(0));
		}
		
		result = taMatcher.replaceAll(tempPrefix + "TEXTAREA" + tempSuffix);
		
		//preserve SCRIPT tags
		String scriptRule = "<script[^>]*?>.*?</script>";
		Pattern scriptPattern = Pattern.compile(scriptRule, Pattern.DOTALL | Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
		
		Matcher scriptMatcher = scriptPattern.matcher(result);
		while(scriptMatcher.find()) {
			scriptBlocks.add(scriptMatcher.group(0));
		}
		
		result = scriptMatcher.replaceAll(tempPrefix + "SCRIPT" + tempSuffix);
		
		//preserve STYLE tags
		String styleRule = "<style[^>]*?>.*?</style>";
		Pattern stylePattern = Pattern.compile(styleRule, Pattern.DOTALL | Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
		
		Matcher styleMatcher = stylePattern.matcher(result);
		while(styleMatcher.find()) {
			styleBlocks.add(styleMatcher.group(0));
		}
		
		result = styleMatcher.replaceAll(tempPrefix + "STYLE" + tempSuffix);
		
		//process pure html
		result = processHtml(result);
		
		//process preserved blocks
		result = processPreBlocks(result, preBlocks);
		result = processTextareaBlocks(result, taBlocks);
		result = processScriptBlocks(result, scriptBlocks);
		result = processStyleBlocks(result, styleBlocks);
		
		return result.trim();
	}
	
	private String processHtml(String html) throws Exception {
		String result = html;
		
		//remove comments
		Pattern commentPattern = Pattern.compile("<!--.*?-->", Pattern.DOTALL | Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
		result = commentPattern.matcher(result).replaceAll("");
		
		//remove extra whitespace characters
		result = result.replaceAll("\\s{2,}"," ");
		
		return result;
	}
	
	private String processPreBlocks(String html, List<String> blocks) throws Exception {
		String result = html;
		
		//put preserved blocks back
		while(result.contains(tempPrefix + "PRE" + tempSuffix)) {
			result = result.replaceFirst(tempPrefix + "PRE" + tempSuffix, Matcher.quoteReplacement(blocks.remove(0)));
		}
		
		return result;
	}
	
	private String processTextareaBlocks(String html, List<String> blocks) throws Exception {
		String result = html;
		
		//put preserved blocks back
		while(result.contains(tempPrefix + "TEXTAREA" + tempSuffix)) {
			result = result.replaceFirst(tempPrefix + "TEXTAREA" + tempSuffix, Matcher.quoteReplacement(blocks.remove(0)));
		}
		
		return result;
	}
	
	private String processScriptBlocks(String html, List<String> blocks) throws Exception {
		String result = html;
		
		if(compressJavaScript) {
			for(int i = 0; i < blocks.size(); i++) {
				blocks.set(i, compressJavaScript(blocks.get(i)));
			}
		}
		
		//put preserved blocks back
		while(result.contains(tempPrefix + "SCRIPT" + tempSuffix)) {
			result = result.replaceFirst(tempPrefix + "SCRIPT" + tempSuffix, Matcher.quoteReplacement(blocks.remove(0)));
		}
		
		return result;
	}
	
	private String processStyleBlocks(String html, List<String> blocks) throws Exception {
		String result = html;
		
		if(compressCss) {
			for(int i = 0; i < blocks.size(); i++) {
				blocks.set(i, compressCssStyles(blocks.get(i)));
			}
		}
		
		//put preserved blocks back
		while(result.contains(tempPrefix + "STYLE" + tempSuffix)) {
			result = result.replaceFirst(tempPrefix + "STYLE" + tempSuffix, Matcher.quoteReplacement(blocks.remove(0)));
		}
		
		return result;
	}
	
	private String compressJavaScript(String source) throws Exception {
		
		String scriptRule = "<script[^>]*?>(.+?)</script>";
		Pattern scriptPattern = Pattern.compile(scriptRule, Pattern.DOTALL | Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
		
		//check if block is not empty
		Matcher scriptMatcher = scriptPattern.matcher(source);
		if(scriptMatcher.find()) {
			
			//call YUICompressor
			StringWriter result = new StringWriter();
			JavaScriptCompressor compressor = new JavaScriptCompressor(new StringReader(scriptMatcher.group(1)), null);
			compressor.compress(result, yuiJsLineBreak, !yuiJsNoMunge, false, yuiJsPreserveAllSemiColons, yuiJsDisableOptimizations);
			
			return source.substring(0, scriptMatcher.start(1)) + result.toString() + source.substring(scriptMatcher.end(1));
		
		} else {
			return source;
		}
	}
	
	private String compressCssStyles(String source) throws Exception {
		
		String styleRule = "<style[^>]*?>(.+?)</style>";
		Pattern stylePattern = Pattern.compile(styleRule, Pattern.DOTALL | Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
		
		//check if block is not empty
		Matcher styleMatcher = stylePattern.matcher(source);
		if(styleMatcher.find()) {
			
			//call YUICompressor
			StringWriter result = new StringWriter();
			CssCompressor compressor = new CssCompressor(new StringReader(styleMatcher.group(1)));
			compressor.compress(result, yuiCssLineBreak);
			
			return source.substring(0, styleMatcher.start(1)) + result.toString() + source.substring(styleMatcher.end(1));
		
		} else {
			return source;
		}
	}

	/**
	 * Returns <code>true</code> if JavaScript compression is enabled.
	 * 
	 * @return current state of JavaScript compression.
	 */
	public boolean isCompressJavaScript() {
		return compressJavaScript;
	}

	/**
	 * Enables JavaScript compression within &lt;script> tags using 
	 * <a href="http://developer.yahoo.com/yui/compressor/">Yahoo YUI Compressor</a> 
	 * if set to <code>true</code>. Default is false for performance reasons.
	 *  
	 * <p><b>Note:</b> Compressing JavaScript is not recommended if pages are 
	 * compressed dynamically on-the-fly because of performance impact. 
	 * You should consider putting JavaScript into a separate file and
	 * compressing it using standalone YUICompressor for example.</p>
	 * 
	 * @param compressJavaScript set <code>true</code> to enable JavaScript compression. 
	 * Default is <code>false</code>
	 * 
	 * @see <a href="http://developer.yahoo.com/yui/compressor/">Yahoo YUI Compressor</a>
	 * 
	 */
	public void setCompressJavaScript(boolean compressJavaScript) {
		this.compressJavaScript = compressJavaScript;
	}

	/**
	 * Returns <code>true</code> if CSS compression is enabled.
	 * 
	 * @return current state of CSS compression.
	 */
	public boolean isCompressCss() {
		return compressCss;
	}

	/**
	 * Enables CSS compression within &lt;style> tags using 
	 * <a href="http://developer.yahoo.com/yui/compressor/">Yahoo YUI Compressor</a> 
	 * if set to <code>true</code>. Default is false for performance reasons.
	 *  
	 * <p><b>Note:</b> Compressing CSS is not recommended if pages are 
	 * compressed dynamically on-the-fly because of performance impact. 
	 * You should consider putting CSS into a separate file and
	 * compressing it using standalone YUICompressor for example.</p>
	 * 
	 * @param compressCss set <code>true</code> to enable CSS compression. 
	 * Default is <code>false</code>
	 * 
	 * @see <a href="http://developer.yahoo.com/yui/compressor/">Yahoo YUI Compressor</a>
	 * 
	 */
	public void setCompressCss(boolean compressCss) {
		this.compressCss = compressCss;
	}

	/**
	 * Returns <code>true</code> if Yahoo YUI Compressor
	 * will only minify javascript without obfuscating local symbols. 
	 * This corresponds to <code>--nomunge</code> command line option.  
	 *   
	 * @return <code>nomunge</code> parameter value used for JavaScript compression.
	 * 
	 * @see <a href="http://developer.yahoo.com/yui/compressor/">Yahoo YUI Compressor</a>
	 */
	public boolean isYuiJsNoMunge() {
		return yuiJsNoMunge;
	}

	/**
	 * Tells Yahoo YUI Compressor to only minify javascript without obfuscating 
	 * local symbols. This corresponds to <code>--nomunge</code> command line option. 
	 * This option has effect only if JavaScript compression is enabled. 
	 * Default is <code>false</code>.
	 * 
	 * @param yuiJsNoMunge set <code>true<code> to enable <code>nomunge</code> mode
	 * 
	 * @see <a href="http://developer.yahoo.com/yui/compressor/">Yahoo YUI Compressor</a>
	 */
	public void setYuiJsNoMunge(boolean yuiJsNoMunge) {
		this.yuiJsNoMunge = yuiJsNoMunge;
	}

	/**
	 * Returns <code>true</code> if Yahoo YUI Compressor
	 * will preserve unnecessary semicolons during JavaScript compression. 
	 * This corresponds to <code>--preserve-semi</code> command line option.
	 *   
	 * @return <code>preserve-semi</code> parameter value used for JavaScript compression.
	 * 
	 * @see <a href="http://developer.yahoo.com/yui/compressor/">Yahoo YUI Compressor</a>
	 */
	public boolean isYuiJsPreserveAllSemiColons() {
		return yuiJsPreserveAllSemiColons;
	}

	/**
	 * Tells Yahoo YUI Compressor to preserve unnecessary semicolons 
	 * during JavaScript compression. This corresponds to 
	 * <code>--preserve-semi</code> command line option. 
	 * This option has effect only if JavaScript compression is enabled.
	 * Default is <code>false</code>.
	 * 
	 * @param yuiJsPreserveAllSemiColons set <code>true<code> to enable <code>preserve-semi</code> mode
	 * 
	 * @see <a href="http://developer.yahoo.com/yui/compressor/">Yahoo YUI Compressor</a>
	 */
	public void setYuiJsPreserveAllSemiColons(boolean yuiJsPreserveAllSemiColons) {
		this.yuiJsPreserveAllSemiColons = yuiJsPreserveAllSemiColons;
	}

	/**
	 * Returns <code>true</code> if Yahoo YUI Compressor
	 * will disable all the built-in micro optimizations during JavaScript compression. 
	 * This corresponds to <code>--disable-optimizations</code> command line option.
	 *   
	 * @return <code>disable-optimizations</code> parameter value used for JavaScript compression.
	 * 
	 * @see <a href="http://developer.yahoo.com/yui/compressor/">Yahoo YUI Compressor</a>
	 */
	public boolean isYuiJsDisableOptimizations() {
		return yuiJsDisableOptimizations;
	}
	
	/**
	 * Tells Yahoo YUI Compressor to disable all the built-in micro optimizations
	 * during JavaScript compression. This corresponds to 
	 * <code>--disable-optimizations</code> command line option. 
	 * This option has effect only if JavaScript compression is enabled.
	 * Default is <code>false</code>.
	 * 
	 * @param yuiJsDisableOptimizations set <code>true<code> to enable 
	 * <code>disable-optimizations</code> mode
	 * 
	 * @see <a href="http://developer.yahoo.com/yui/compressor/">Yahoo YUI Compressor</a>
	 */
	public void setYuiJsDisableOptimizations(boolean yuiJsDisableOptimizations) {
		this.yuiJsDisableOptimizations = yuiJsDisableOptimizations;
	}
	
	/**
	 * Returns number of symbols per line Yahoo YUI Compressor
	 * will use during JavaScript compression. 
	 * This corresponds to <code>--line-break</code> command line option.
	 *   
	 * @return <code>line-break</code> parameter value used for JavaScript compression.
	 * 
	 * @see <a href="http://developer.yahoo.com/yui/compressor/">Yahoo YUI Compressor</a>
	 */
	public int getYuiJsLineBreak() {
		return yuiJsLineBreak;
	}

	/**
	 * Tells Yahoo YUI Compressor to break lines after the specified number of symbols 
	 * during JavaScript compression. This corresponds to 
	 * <code>--line-break</code> command line option. 
	 * This option has effect only if JavaScript compression is enabled.
	 * Default is <code>-1</code> to disable line breaks.
	 * 
	 * @param yuiJsLineBreak set number of symbols per line
	 * 
	 * @see <a href="http://developer.yahoo.com/yui/compressor/">Yahoo YUI Compressor</a>
	 */
	public void setYuiJsLineBreak(int yuiJsLineBreak) {
		this.yuiJsLineBreak = yuiJsLineBreak;
	}
	
	/**
	 * Returns number of symbols per line Yahoo YUI Compressor
	 * will use during CSS compression. 
	 * This corresponds to <code>--line-break</code> command line option.
	 *   
	 * @return <code>line-break</code> parameter value used for CSS compression.
	 * 
	 * @see <a href="http://developer.yahoo.com/yui/compressor/">Yahoo YUI Compressor</a>
	 */
	public int getYuiCssLineBreak() {
		return yuiCssLineBreak;
	}
	
	/**
	 * Tells Yahoo YUI Compressor to break lines after the specified number of symbols 
	 * during CSS compression. This corresponds to 
	 * <code>--line-break</code> command line option. 
	 * This option has effect only if CSS compression is enabled.
	 * Default is <code>-1</code> to disable line breaks.
	 * 
	 * @param yuiCssLineBreak set number of symbols per line
	 * 
	 * @see <a href="http://developer.yahoo.com/yui/compressor/">Yahoo YUI Compressor</a>
	 */
	public void setYuiCssLineBreak(int yuiCssLineBreak) {
		this.yuiCssLineBreak = yuiCssLineBreak;
	}
	
}