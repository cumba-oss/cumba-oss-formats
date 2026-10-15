
/* make-sas-oracle.sas -- PLAN-sas7bdat-rle-control-codes (register Q4: RLE control codes 0x2n / 0x3n).
   Synthetic data only. Set OUT to an empty folder, run once, hand back the folder. */
%let out = %SYSFUNC(pathname(WORK));
libname o "&out";

/* ---- run metadata ---- */
data _null_;
  file "&out\oracle-meta.csv";
  put "key,value";
  put "sysvlong,&sysvlong";
  put "sysscpl,&sysscpl";
  put "sysencoding,&sysencoding";
  put "started," "%sysfunc(datetime(), E8601DT23.3)";
run;

/* ---- Q4: literal (non-repeating) stretches of every length 1..400 ----
   Consecutive bytes always differ (step 7 mod 90 over printable ASCII), so RLE can only encode the
   stretch as literal copies -- the 0x0n..0x3n family, including any COPY96 (0x2n) SAS uses.
   Each dataset exists COMPRESSED and UNCOMPRESSED: the uncompressed twin is the oracle. */
%macro literal(name, comp);
data o.&name(compress=&comp);
  length id 8 txt $1000;
  do n = 1 to 400;
    txt = ' ';
    do i = 1 to n;
      substr(txt, i, 1) = byte(33 + mod(i * 7, 90));
    end;
    id = n;
    output;
  end;
  keep id txt;
run;
%mend;
%literal(lit_char, char)
%literal(lit_plain, no)

/* literal stretches followed by a long run, so a literal copy is followed by a repeat command
   (the cursor after a COPY96 is what goes wrong if the operand byte is misread) */
%macro mixed(name, comp);
data o.&name(compress=&comp);
  length id 8 txt $1000;
  do n = 60 to 140;
    do r = 3, 20, 300;
      txt = ' ';
      do i = 1 to n;
        substr(txt, i, 1) = byte(33 + mod(i * 7, 90));
      end;
      substr(txt, n + 1, r) = repeat('Q', r - 1);
      substr(txt, n + r + 1, 5) = 'END!!';
      id = n * 1000 + r;
      output;
    end;
  end;
  keep id txt;
run;
%mend;
%mixed(mix_char, char)
%mixed(mix_plain, no)

/* every repeat command: runs of 'A', blanks, '@' and zero bytes of length 1..300 */
%macro runs(name, comp);
data o.&name(compress=&comp);
  length id 8 kind $1 txt $1000;
  do kind = 'A', ' ', '@', '0';
    do n = 1 to 300;
      if kind = '0' then txt = repeat('00'x, n - 1);
      else txt = repeat(kind, n - 1);
      substr(txt, n + 1, 3) = 'xyz';
      id = n;
      output;
    end;
  end;
  keep id kind txt;
run;
%mend;
%runs(run_char, char)
%runs(run_plain, no)

/* ---- what SAS says about each file ---- */
ods listing file = "&out\contents.txt";
proc contents data = o._all_;
run;
ods listing close;