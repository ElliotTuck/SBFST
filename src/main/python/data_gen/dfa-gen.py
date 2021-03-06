#
# A script to Generate strings that contain exactly 2 'b's
#
import pynini
import functools
import numpy as np
import random

A = functools.partial(pynini.acceptor)
T = functools.partial(pynini.transducer)
e = pynini.epsilon_machine()

# Defining alphabet

alpha = "abcd"

zero = (e - e).optimize()
sigma = zero
for x in list(alpha):
    sigma = A(x) | sigma
sigma.optimize()


# Utility function that outputs all strings of an fsa

def listStringSet(acceptor):
    mylist = []
    paths = acceptor.paths()
    for s in paths.ostrings():
        mylist.append(s)
    mylist.sort(key=len)
    return mylist


def gen_one_edit_distance_str(w):
    sigma_star = pynini.closure(sigma).optimize()
    sigma_plus = (sigma + sigma_star).optimize()

    ins1 = sigma_star + T("", "[I]") + sigma_star
    sub1 = sigma_star + T("", "[S]") + sigma_plus
    del1 = sigma_star + T("", "[D]") + sigma_plus

    edit1 = (ins1 | sub1 | del1).optimize()

    replaceI = zero
    for x in list(alpha):
        replaceI = replaceI | T("[I]", x)
    replaceI.optimize()

    replaceD = zero
    for x in list(alpha):
        replaceD = replaceD | T("[D]" + x, "")
    replaceD.optimize()

    replaceS = zero
    for x in list(alpha):
        for y in list(alpha):
            if x != y:
                replaceS = replaceS | T("[D]" + x, y)

    editTransducer = pynini.closure(sigma | replaceI | replaceD | replaceS)
    editTransducer.optimize()

    wBall = (w @ edit1 @ editTransducer).optimize()
    return list(set(listStringSet(wBall)))


# Utility function that gets the strings of an fsa
# with length from min_len to max_len

def getPosString(fsa, min_len, max_len):
    fsa_dict = {}
    pos_str_dict = {}
    for i in range(min_len, max_len + 1):
        fsa_dict[i] = pynini.intersect(fsa, pynini.closure(sigma, i, i))
        pos_str_dict[i] = list(np.random.permutation(listStringSet(fsa_dict[i])))
        print(pos_str_dict[i])
    return pos_str_dict


# Utility function that gets the strings of the complement
# of an fsa with length from min_len to max_len

def getNegString(fsa, min_len, max_len):
    fsa_dict = {}
    neg_str_dict = {}
    for i in range(min_len, max_len + 1):
        fsa_dict[i] = pynini.difference(pynini.closure(sigma, i, i), fsa)
        neg_str_dict[i] = list(np.random.permutation(listStringSet(fsa_dict[i])))
        print(neg_str_dict[i])
    return neg_str_dict


# create train/dev/test from positive and negative strings
# generated by fsa. the positive/negative examples have no
# overlap with a given forbidden set


def create_data_no_duplicate(filename, forbidden, min_len, max_len, num):
    result = []
    with open(filename, "w+") as f:
        for i in range(min_len, max_len + 1):
            count = 0
            for j in range(len(pos_str_dict[i])):
                if pos_str_dict[i][j] in forbidden:
                    continue
                else:
                    f.write(pos_str_dict[i][j] + "\t" + "True\n")
                    result.append(pos_str_dict[i][j])
                    count = count + 1
                    if count == num:
                        break
            count = 0
            for j in range(len(neg_str_dict[i])):
                if neg_str_dict[i][j] in forbidden:
                    continue
                else:
                    f.write(neg_str_dict[i][j] + "\t" + "False\n")
                    result.append(neg_str_dict[i][j])
                    count = count + 1
                    if count == num:
                        break
    return result


# create train/dev/test data by randomly sampling {num} of
# positve and negative examples for each length.
# This may contain duplicate.


def create_data_with_duplicate(filename, min_len, max_len, num):
    result = []
    with open(filename, "w+") as f:
        for i in range(min_len, max_len + 1):
            for j in range(num):
                index = random.randint(0, len(pos_str_dict[i]) - 1)
                f.write(pos_str_dict[i][index] + "\t" + "True\n")
                result.append(pos_str_dict[i][index])
            for j in range(num):
                index = random.randint(0, len(neg_str_dict[i]) - 1)
                f.write(neg_str_dict[i][index] + "\t" + "False\n")
                result.append(neg_str_dict[i][index])
    return result


# create adversarial pairs

def create_adversarial_data(filename, min_len, max_len, num):
    with open(filename, "w+") as f:
        for i in range(min_len, max_len + 1):
            count = 0
            for pos_str in pos_str_dict[i]:
                one_edit_list = gen_one_edit_distance_str(A(pos_str))
                one_edit_list = list(np.random.permutation(one_edit_list))
                for item in one_edit_list:
                    if item in neg_str_dict[i]:
                        count = count + 1
                        f.write(pos_str + "\t" + "True\n")
                        f.write(item + "\t" + "False\n")
                        break
                if count == num:
                    break


# define FSA

my_fsa = A("a").closure() | A("b").closure() | A("c").closure()


# define hyper-parameters

ss_min_len = 2
ss_max_len = 4
train_pos_num = 5
dev1_pos_num = 3
test1_pos_num = 4
dev2_pos_num = 3
test2_pos_num = 4
ls_min_len = 5
ls_max_len = 7
test3_pos_num = 4
test4_pos_num = 4


# generate short strings and construct a dictionary where
# key=length, value=a list of strings generated by fsa

pos_str_dict = getPosString(my_fsa, ss_min_len, ss_max_len)
neg_str_dict = getNegString(my_fsa, ss_min_len, ss_max_len)


# create training data with duplicates

train = create_data_with_duplicate("train.txt", ss_min_len, ss_max_len, train_pos_num)


# create dev_1 and test_1 (with duplicates)
create_data_with_duplicate("dev_1.txt", ss_min_len, ss_max_len, dev1_pos_num)
create_data_with_duplicate("test_1.txt", ss_min_len, ss_max_len, test1_pos_num)


# create dev_2 and test_2 (no duplicates, no overlap in train/dev/test data)
dev2 = create_data_no_duplicate("dev_2.txt", train, ss_min_len, ss_max_len, dev2_pos_num)
create_data_no_duplicate("test_2.txt", train + dev2, ss_min_len, ss_max_len, test2_pos_num)


# generate long strings

pos_str_dict = getPosString(my_fsa, ls_min_len, ls_max_len)
neg_str_dict = getNegString(my_fsa, ls_min_len, ls_max_len)


# create test_3 (no duplicates, no overlap in dev/test data)
create_data_no_duplicate("test_3.txt", [], ls_min_len, ls_max_len, test3_pos_num)


# create test_4 (adversarial examples)
create_adversarial_data("test_4.txt", ls_min_len, ls_max_len, test4_pos_num)
