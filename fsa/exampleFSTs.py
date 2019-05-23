#
# A script to write out some simple FSAs
#

import pynini
import functools

A = functools.partial(pynini.acceptor) 
e = pynini.epsilon_machine()
zero = e - e
zero.optimize()

# Defining sigma and sigmastar
sigma4 = zero
for x in list("abcd"): sigma4 = A(x) | sigma4
sigma4.optimize()

sigma4Star = (sigma4.star).optimize()

a = A("a")
b = A("b")
c = A("c")
d = A("d")

# define simpler sigma and sigmastar
sigma2 = zero
for x in "ab": sigma2 = A(x) | sigma2
sigma2.optimize()
sigma2Star = sigma2.star.optimize()


def lg_containing_str(x,i):
    # return (sigma4Star + pynini.closure(b,i,i) + sigma4Star).minimize()
    return (sigma4Star + pynini.closure(x,i,i) + sigma4Star).minimize()


def lg_containing_ssq(x,i):
    return (pynini.closure(sigma4Star + x + sigma4Star,i,i)).minimize()


def lg_with_str(sigmastar, x, i):
    return (sigmastar + pynini.closure(x, i, i) + sigmastar).minimize()


def lg_with_ssq(sigmastar, x, i):
    return (pynini.closure(sigmastar + x + sigmastar, i, i)).minimize()


###############
# SL Examples #
###############

sl=dict()

sl[0] = sigma4Star - lg_containing_str(b,2)  # SL2 , forbidden bb
sl[1] = sigma4Star - lg_containing_str(b,4)  # SL4 , forbidden bbbb
sl[2] = sigma4Star - lg_containing_str(b,8)  # SL8 , forbidden bbbbbbbb

###############
# SP Examples #
###############

sp=dict()
sp[0] = sigma4Star - lg_containing_ssq(b,2)     # SP2 , forbidden bb
sp[1] = sigma4Star - lg_containing_ssq(b,4)     # SP4 , forbidden bbbb
sp[2] = sigma4Star - lg_containing_ssq(b,8)     # SP8 , forbidden bbbbbbbb

###############
# LT Examples #
###############

lt=dict()
# LT2 , at least one bb
lt[0] = lg_containing_str(b,2)

# LT4 , at least one bbbb or at least one aaaa
lt[1] = pynini.union(lg_containing_str(b,4), lg_containing_str(a,4))
# lt[1] = lg_containing_str(b,4) + lg_containing_str(a,4)

# LT4 , at least one bbbb and at least one aaaa
lt[2] = pynini.intersect(lg_containing_str(b,4), lg_containing_str(a,4))
        
# LT8 , if b^8 then a^8 (~~~ not b^8 or a^8)
lt[3] = (sigma4Star - lg_containing_str(b,8)) | lg_containing_str(a,8)

# aa and ab substrings
lt[4] = pynini.intersect(lg_containing_str(a, 2), lg_containing_str(a + b, 1))

# aa and ab substrings (using sigma = {a,b})
lt[5] = pynini.intersect(lg_with_str(sigma2Star, a, 2), lg_with_str(sigma2Star, a + b, 1))

###############
# PT Examples #
###############

pt=dict()
# PT2 , at least one bb
pt[0] = lg_containing_ssq(b,2)

# PT4 , at least one bbbb or at least one aaaa
pt[1] = pynini.union(lg_containing_ssq(b,4), lg_containing_ssq(a,4))
# pt[1] = lg_containing_ssq(b,4) + lg_containing_ssq(a,4)

# PT4 , at least one bbbb and at least one aaaa
pt[2] = pynini.intersect(lg_containing_ssq(b,4), lg_containing_ssq(a,4))
        
# PT8 , if b^8 then a^8 (~~~ not b^8 or a^8)
pt[3] = (sigma4Star - lg_containing_ssq(b,8)) | lg_containing_ssq(a,8)

# aa and ab subsequences
pt[4] = pynini.intersect(lg_containing_ssq(a, 2), sigma4Star + a + sigma4Star + b + sigma4Star)

# aa and ab subsequences (using sigma = {a,b})
pt[5] = pynini.intersect(lg_with_ssq(sigma2Star, a, 2), sigma2Star + a + sigma2Star + b + sigma2Star)


################
# LTT Examples #
################
ltt=dict()

# LTT t=2, k=1 "exactly two bs"
atleast2bs = (lg_containing_str(b,1) + lg_containing_str(b,1)).optimize()
forbid3bs = (sigma4Star - lg_containing_ssq(b,3)).optimize()
ltt[0] = atleast2bs * forbid3bs

# LTT t=2, k=2 "exactly two bb substrings"
atleast2bbs = (lt[0] + lt[0]).optimize()
forbid3bbs = sigma4Star - (atleast2bbs + lt[0]).optimize()
ltt[1] = atleast2bbs * forbid3bbs

# LTT t=5, k=2 "exactly five bb substrings"
atleast5bbs = pynini.closure(lt[0], 5, 5).optimize()
forbid6bbs = sigma4Star - pynini.closure(lt[0], 6, 6).optimize()
ltt[2] = atleast5bbs * forbid6bbs

# LTT t=5, k=8 "exactly five b^8 substrings"
atleast1b8 = pynini.closure(lg_containing_str(b,8), 5, 5).optimize()
forbid6b8s = sigma4Star - pynini.closure(lg_containing_str(b,8), 6, 6).optimize()
ltt[3] = atleast1b8 * forbid6b8s


######################
# Star-Free Examples #
######################
sf=dict()

# every pair of 'b's has at least one 'a' in between
cdstar = sigma4Star - (lg_containing_str(a,1) | lg_containing_str(b,1))
cdstar.optimize()

sf[0] = pynini.closure(cdstar + b + cdstar + a + cdstar + b + cdstar)

# every pair of 'b's has at least one 'a' in between and every pair of 'a's has at least one 'b' in between

sf[1] = sf[0] * pynini.closure(cdstar + a + cdstar + b + cdstar + a + cdstar)

sf[2] = sf[0] * ltt[0]

# every pair of 'bb's has 'aa' in between
filler = sigma4Star - (lg_containing_str(b,2) | lg_containing_str(a,2))
sf[3] = pynini.closure(filler + b+b + filler + a+a + filler + b+b + filler)


####################
# Regular Examples #
####################
reg=dict()

evena = pynini.closure(lg_containing_ssq(a,2))
evenb = pynini.closure(lg_containing_ssq(b,2))
mod4a = pynini.closure(lg_containing_ssq(a,4))
mod8a = pynini.closure(lg_containing_ssq(a,8))

reg[0] = evena
reg[1] = mod4a
reg[2] = mod8a
reg[3] = evena  * evenb
reg[4] = sl[1]  * evena
reg[5] = sp[1]  * evena
reg[6] = lt[1]  * evena
reg[7] = pt[1]  * evena
reg[8] = ltt[1] * evena


pair_names=[(sl,'sl'),
            (sp,'sp'),
            (lt,'lt'),
            (pt,'pt'),
            (ltt,'ltt'),
            (sf,'sf'),
            (reg,'reg')]

lg_classes = dict(enumerate(pair_names))

# Minimizing the acceptors
#          and
# Writing them to files

for x in lg_classes:
    lg_class,name = lg_classes[x]
    for i in list(range(len(lg_class))):
        lg_class[i].optimize()
        filename = name+str(i)
        # lg_class[i].write(filename+".fsa")
        with open('../src/test/resources/' + filename + '.fst.txt', 'w') as f_out:
            f_out.write(str(lg_class[i]))
        with open('../src/test/resources/' + filename + '.states.syms', 'w') as f_out:
            for i in range(lg_class[i].num_states()):
                f_out.write(str(i) + '\t' + str(i) + '\n')
        with open('../src/test/resources/' + filename + '.input.syms', 'w') as f_out:
            f_out.write('a\t0\nb\t1\nc\t2\nd\t3')
        with open('../src/test/resources/' + filename + '.output.syms', 'w') as f_out:
            f_out.write('a\t0\nb\t1\nc\t2\nd\t3')
        # print(filename)
        # print(lg_class[i])
        # include only if you want to see a drawing of the fsa
        # a.draw(filename+".dot",title=filename,acceptor=True)

        
# from the command line, try
# fstprint a1.fst
# etc
# 
