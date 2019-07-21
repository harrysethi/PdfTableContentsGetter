/**
 * @author harinderpal
 *
 */

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.PDFTextStripperByArea;

import java.io.*;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class PdfTableContentsGetter {

	static int FIRST_LINE_TO_LOOK = 3; // highly unlikely that first 3 lines are from the index
	static int MAX_LINES_TO_LOOK = 500; // unlikely to have index after 500 lines
	
	static int MAX_LINE_LENGTH = 100; // unlikely to have a index line with > 100 lines
	static int MIN_LINE_LENGTH = 7; // unlikely to have a index line with > 100 lines
	static int MIN_NON_DIGITS_LENGTH = 3; // unlikely to have just numbers, assuming atleast 5 non digits
	
	//static int MAX_PAGE_NUMBER = 500; // assuming max 500 size book
	//static int MAX_PAGE_NUMBER_LENGTH = Integer.toString(MAX_PAGE_NUMBER).length();
	
	static int MIN_INDEX_LINES = 10; // assuming there will be atleast 10 table of contents
	static int MAX_NON_INDEX_LINES_AFTER_INDEX_FOUND = 5;
	
	static int MAX_NUMBERS_IN_LINE = 10; // assuming a content line should not have > 5 numbers
	
	static int MIN_FIRST_PAGE_NUM = 30;
	static int THRESHOLD_LAST_PAGE_NUM = 50;
	
	/**
	 * @param args
	 * @throws IOException 
	 */
	public static void main(String[] args) throws IOException {
		
		String filePath = "files/AGS-2018.pdf";
		
		try (PDDocument document = PDDocument.load(new File(filePath))) {
            document.getClass();
			  
			if (!document.isEncrypted()) {
			
                PDFTextStripperByArea stripper = new PDFTextStripperByArea();
                stripper.setSortByPosition(true);

                PDFTextStripper tStripper = new PDFTextStripper();

                String pdfFileInText = tStripper.getText(document);

				// split by whitespace
                String lines[] = pdfFileInText.split("\\r?\\n");                
                
				//String lines[] = {"","","","2016 in brief  ...................................................................... 1"};

                List<String> contents = getContents(lines, document.getNumberOfPages());

                
                System.out.println("-------------------------------------");
                System.out.println("-------------CONTENTS----------------");
                System.out.println("-------------------------------------");
                
                BufferedWriter bw = new BufferedWriter(new FileWriter(filePath + "_index.txt"));           
            
                for(String content : contents) 
                {
                	System.out.println(content);
                	bw.write(content);
                	bw.newLine();
                }

                bw.close();
            }

        }
	}
	
	static enum State_enum 
	{
		SEARCHING_FOR_INDEX,
		INDEX_FOUND
	}
	
	static List<String> getContents(String lines[], int pagesInDocument)
	{
		int MAX_PAGE_NUMBER = pagesInDocument;
		int MAX_PAGE_NUMBER_LENGTH = Integer.toString(MAX_PAGE_NUMBER).length() + 2; //todo
		
		System.out.println("=====info: number of pages: " + MAX_PAGE_NUMBER);
		State_enum STATE = State_enum.SEARCHING_FOR_INDEX;
		
		int numberOfLinesFoundToBeIndexLines = 0;
		int numberOfLinesFoundToBeNotIndexLinesAfterIndexFound = 0;
		
		Pattern pattern_digit = Pattern.compile(".*\\d.*");
		Pattern pattern_just_digit = Pattern.compile("\\d{1," + MAX_PAGE_NUMBER_LENGTH + "}");
		
		List<String> contents = new ArrayList<>();
		
		int maxPageNumSeenSoFar = 0;
		
		for(int i = FIRST_LINE_TO_LOOK; i < MAX_LINES_TO_LOOK && i < lines.length; i++) 
		{
			String line = lines[i];
			System.out.println();
			System.out.println("#" + i);
			System.out.println("info: line = " + line);
			
			line = line.trim();
			
			if (line.endsWith("."))
			{
				line = line.substring(0,line.length()-1);
				try
				{
					Integer.parseInt(String.valueOf(line.charAt(0)));
				}
				catch(Exception e)
				{
					System.out.println("debug: ---Ignoring--: line ends with '.' and first char is not integer---");
					continue;
				}
			}
			
			System.out.println("info: trimmedlineLength = " + line.length());
			
			// ---- Check the line length
			if (line.length() > MAX_LINE_LENGTH)
			{
				System.out.println("debug: ---Ignoring--: line too big to be a index line---");
				continue;
			}
			if (line.length() < MIN_LINE_LENGTH)
			{
				System.out.println("debug: ---Ignoring--: line too small to be a index line---");
				continue;
			}
			
			// -----  Check if the string contains a number ---
			Matcher matcher_digit = pattern_digit.matcher(line);
			
			if (!matcher_digit.matches()) 
			{
				System.out.println("debug: ---Ignoring--: Does not contain a number---");
				continue; 
			}
			
			// --- Check count of numbers in the string --- 
			String[] splitString_just_digit = pattern_just_digit.split(line);
			System.out.println("debug: splitString_just_digit = " + splitString_just_digit.length);
			
			if (splitString_just_digit.length > MAX_NUMBERS_IN_LINE)
			{
				System.out.println("debug: ---Ignoring--: too much of numbers in a line---");
				continue;
			}
			
			// --- Check should have atleast one number in the line which is 1-500
			Matcher matcher_just_digit = pattern_just_digit.matcher(line);
			
			boolean foundRequiredDigit = false; //requiredDigit is a digit which is prob a page number
			while (matcher_just_digit.find())
			{
				int digit = Integer.parseInt(matcher_just_digit.group());
				System.out.println("debug: digit = " + digit);
				
				int digit_startIndex = matcher_just_digit.start();
				int digit_endIndex = matcher_just_digit.end();
				
				System.out.println("debug: digit_StartIndex = " + digit_startIndex);
				System.out.println("debug: digit_endIndex = " + digit_endIndex);
				
				if (digit < 0 || digit > MAX_PAGE_NUMBER) 
				{
					System.out.println("debug: ---next digit--: page num not in the range---");
					continue; 
				}
				
				if (STATE == State_enum.SEARCHING_FOR_INDEX && digit > MIN_FIRST_PAGE_NUM)
				{
					System.out.println("debug: ---next digit--: first digit can't be too big---");
					continue; 
				}
								
				if (digit_startIndex == 0 || digit_endIndex == line.length())
				{
					if (digit < maxPageNumSeenSoFar && contents.size() > MIN_INDEX_LINES && maxPageNumSeenSoFar > MAX_PAGE_NUMBER - THRESHOLD_LAST_PAGE_NUM)
					{
						System.out.println("~~~~~~~info: new digit can not be less than the maxPageNumSeenSoFar, seems to be done---");
						return contents;
					}
					
					System.out.println("info: foundIndexLine");
					foundRequiredDigit = true;
					numberOfLinesFoundToBeIndexLines ++;
					numberOfLinesFoundToBeNotIndexLinesAfterIndexFound = 0;
					STATE = State_enum.INDEX_FOUND;
					maxPageNumSeenSoFar = digit;
				}
			}
			
			if (!foundRequiredDigit) 
			{
				if (STATE == State_enum.INDEX_FOUND)
				{
					if (numberOfLinesFoundToBeNotIndexLinesAfterIndexFound == MAX_NON_INDEX_LINES_AFTER_INDEX_FOUND)
					{
						if (numberOfLinesFoundToBeIndexLines < MIN_INDEX_LINES)
						{
							System.out.println("~~~~~~~info: Seems to have read something which is not index, reset the state~~~~~");
							STATE = State_enum.SEARCHING_FOR_INDEX;
							maxPageNumSeenSoFar = 0;
							contents = new ArrayList<>();
							continue;
						}
						else
						{
							System.out.println("~~~~~~~info: Seems to be done with the index, leaving now~~~~~~");
							return contents;	
						}
					}
					numberOfLinesFoundToBeNotIndexLinesAfterIndexFound ++;
				}
				System.out.println("debug: ---Ignoring--: no digit found which can be a page num---");
				continue;
			}
			
			String nonDigits = String.join("", splitString_just_digit);
			nonDigits = nonDigits.trim();
			if(nonDigits.length() < MIN_NON_DIGITS_LENGTH)
			{
				System.out.println("debug: ---Ignoring--: too less non-digits---");
				continue;
			}
			
			//String content = line.replaceAll("^\\d{1,3}+", "");
			//content = line.replaceAll("\\d{1,3}+$", "");
			
			//content = content.trim();
			
			String content = line;
			
			contents.add(content);
						
			System.out.println("~~~~~~info: content = " + content);
			
			String[] words = line.split(" ");
			if (words.length == 2 && (words[0].equalsIgnoreCase("introduction") || words[1].equalsIgnoreCase("introduction")))
			{
				System.out.println("~~~~~~~info: seems to be the start of index, dropping the previous lines taken~~~~~~");
				contents = new ArrayList<>();
				contents.add(content);
				numberOfLinesFoundToBeIndexLines = 1;
				continue;
			}
			
			
			if (content.contains("Glossary"))
			{
				System.out.println("~~~~~~~info: Got the last index term, DONE~~~~~~");
				return contents;
			}
		}
		
		return contents;
	}

}
